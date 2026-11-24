#!/bin/bash
set -euo pipefail
umask 077

die() { printf '%s\n' "$*" >&2; exit 1; }

http_port=8081
db_port=3307
check=false
while [ "$#" -gt 0 ]; do
    case "$1" in
        --http-port|--db-port)
            option=$1
            [ "$#" -ge 2 ] || die "$option needs a port number."
            [[ $2 =~ ^[1-9][0-9]{3,4}$ ]] && (( $2 >= 1024 && $2 <= 65535 )) ||
                die 'Choose a port from 1024 to 65535.'
            if [ "$option" = --http-port ]; then http_port=$2; else db_port=$2; fi
            shift 2
            ;;
        --check) check=true; shift ;;
        --help)
            printf '%s\n' 'Usage: bash Run-PROERP.command [--http-port 8081] [--db-port 3307] [--check]'
            exit 0
            ;;
        *) die "Unknown option: $1. Use --help." ;;
    esac
done

[ "$(uname -s)" = Darwin ] || die 'This launcher runs on macOS. On Windows, use Run-PROERP.bat.'
[ "$http_port" != "$db_port" ] || die 'Choose different ports for Tomcat and MySQL.'
project=$(cd -P -- "$(dirname -- "$0")" && pwd)
arch=$(uname -m)
if [ "$(sysctl -n hw.optional.arm64 2>/dev/null || true)" = 1 ]; then arch=arm64; fi

case "$arch" in
    arm64)
        jdk_arch=aarch64
        jdk_hash=dd01e975635e7d7d21f8862df63ec065c4dbf61a484c6c4468bb6ae022fc6986
        mysql_archive=mysql-8.4.13-macos15-arm64.tar.gz
        mysql_hash=f324f8950e24d4147470bbc6a6ef0b90ef93b91d3fc74154b3d2ba085b44912a
        ;;
    x86_64)
        jdk_arch=x64
        jdk_hash=c430a3474f68cbaa50471b3291a951de408b3cb895943d0519369c75b81884d1
        mysql_archive=mysql-8.4.11-macos15-x86_64.tar.gz
        mysql_hash=90e8aea10698d01b978f0179e72e8d5e2cbe9f9bd5771e88b0b75d7c82244d3f
        ;;
    *) die "No macOS runtime is available for $arch." ;;
esac
major=$(sw_vers -productVersion | cut -d. -f1)
[ "$major" -ge 15 ] || die 'These pinned packages require macOS 15 or newer.'

runtime="$project/.standalone/macos-$arch"
downloads="$runtime/downloads"
logs="$runtime/logs"
data="$runtime/mysql-data"
lock="$runtime/lock"
mkdir -p -- "$downloads" "$logs"
mkdir -- "$lock" 2>/dev/null || die "Another run is starting or active. The launcher lock is at $lock."
run=
socket_dir=
mysql_pid=
tomcat_pid=
unpack=

stop_server() {
    local pid=$1 i
    [ -n "$pid" ] || return 0
    kill -TERM "$pid" 2>/dev/null || return 0
    for ((i=0; i<20; i++)); do
        kill -0 "$pid" 2>/dev/null || { wait "$pid" 2>/dev/null || true; return 0; }
        sleep 1
    done
    printf 'Server process %s is still stopping. See %s\n' "$pid" "$logs" >&2
    return 1
}

cleanup() {
    local code=$? stopped=true
    trap - EXIT
    set +e
    stop_server "$tomcat_pid" || stopped=false
    stop_server "$mysql_pid" || stopped=false
    if $stopped; then
        case "$run" in "$runtime"/run.*) rm -rf -- "$run" ;; esac
        case "$socket_dir" in /tmp/proerp.*) rm -rf -- "$socket_dir" ;; esac
    else
        code=1
    fi
    case "$unpack" in "$runtime"/extract.*) rm -rf -- "$unpack" ;; esac
    rm -f -- "$lock/pid"
    rmdir -- "$lock"
    exit "$code"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM HUP
printf '%s\n' "$$" > "$lock/pid"

for port in "$http_port" "$db_port"; do
    if nc -z -w 1 127.0.0.1 "$port" >/dev/null 2>&1; then
        die "Port $port is in use. Pass a different port with --http-port or --db-port."
    fi
done

matches_checksum() {
    local path=$1 algorithm=$2 expected=$3 actual
    [ -f "$path" ] || return 1
    actual=$(shasum -a "$algorithm" < "$path") || return 1
    [ "$expected" = "${actual%% *}" ]
}

# Keep progress visible while the exit handler owns the staging directory.
install_archive() {
    local name=$1 algorithm=$2 sha=$3 sentinel=$4 url candidate source= archive="$downloads/$1"
    shift 4
    package_dir="$runtime/${name%.tar.gz}"
    package_dir="${package_dir%.zip}"
    [ ! -f "$package_dir/$sentinel" ] || return 0
    if ! matches_checksum "$archive" "$algorithm" "$sha"; then
        printf 'Downloading %s ...\n' "$name"
        for url in "$@"; do
            if curl --fail --silent --show-error --location --proto '=https' --proto-redir '=https' \
                --retry 2 --max-time 600 -o "$archive.part" "$url" &&
                matches_checksum "$archive.part" "$algorithm" "$sha"; then
                mv -f -- "$archive.part" "$archive"
                break
            fi
        done
        matches_checksum "$archive" "$algorithm" "$sha" || die "Download or checksum verification failed for $name."
    fi
    printf 'Extracting %s ...\n' "$name"
    unpack=$(mktemp -d "$runtime/extract.XXXXXX")
    tar -xf "$archive" -C "$unpack"
    for candidate in "$unpack" "$unpack"/*; do
        if [ -f "$candidate/$sentinel" ]; then
            [ -z "$source" ] || die "Unexpected archive layout for $name."
            source=$candidate
        fi
    done
    [ -n "$source" ] && [ ! -e "$package_dir" ] || die "Unexpected archive layout for $name."
    mv -- "$source" "$package_dir"
    if [ -d "$unpack" ]; then rm -rf -- "$unpack"; fi
    unpack=
}

jdk_archive="microsoft-jdk-17.0.21.1-macos-$jdk_arch.tar.gz"
install_archive "$jdk_archive" 256 "$jdk_hash" Contents/Home/bin/javac \
    "https://aka.ms/download-jdk/$jdk_archive"
export JAVA_HOME="$package_dir/Contents/Home"

install_archive apache-tomcat-10.1.62.zip 512 \
    43198b4bd591a681b9effb7795de3f7b07ecc2b2d8c0158a1f8bcf0182bda4f6ca566c61e49371d81d6dcc73b518e18aaa711d5498b05fcc617659e45d96db1e \
    bin/bootstrap.jar \
    https://downloads.apache.org/tomcat/tomcat-10/v10.1.62/bin/apache-tomcat-10.1.62.zip \
    https://archive.apache.org/dist/tomcat/tomcat-10/v10.1.62/bin/apache-tomcat-10.1.62.zip
tomcat=$package_dir

install_archive "$mysql_archive" 256 "$mysql_hash" bin/mysqld \
    "https://cdn.mysql.com/archives/mysql-8.4/$mysql_archive" \
    "https://cdn.mysql.com/Downloads/MySQL-8.4/$mysql_archive"
mysql=$package_dir

driver_name=mysql-connector-j-26.10.0.jar
install_archive mysql-connector-j-26.10.0.zip 256 \
    01a5a26cc5ef52a55301388cabf70e3f44f84de80ea162bf33a0b80f2e181e82 "$driver_name" \
    https://cdn.mysql.com/Downloads/Connector-J/mysql-connector-j-26.10.0.zip \
    https://cdn.mysql.com/archives/mysql-connector-java-26.10/mysql-connector-j-26.10.0.zip
connector=$package_dir

run=$(mktemp -d "$runtime/run.XXXXXX")
# Keep the MySQL socket path short even when the project is nested in a long directory.
socket_dir=$(mktemp -d /tmp/proerp.XXXXXX)
secrets="$runtime/local-secrets"
if [ ! -f "$secrets" ]; then
    [ ! -e "$data" ] || die 'The standalone database exists and its local-secrets file is missing.'
    temp_secrets="$run/local-secrets"
    for role in root app admin customer; do openssl rand -hex 32; done > "$temp_secrets"
    mv -- "$temp_secrets" "$secrets"
fi
{
    read -r root_secret
    read -r db_secret
    read -r admin_secret
    read -r customer_secret
} < "$secrets"
for secret in "$root_secret" "$db_secret" "$admin_secret" "$customer_secret"; do
    [[ $secret =~ ^[a-f0-9]{64}$ ]] || die 'The standalone credential file is invalid.'
done
root_options="$run/mysql-root.cnf"
printf '[client]\npassword=%s\n' "$root_secret" > "$root_options"
mysql_root() {
    "$mysql/bin/mysql" "--defaults-file=$root_options" --no-login-paths --protocol=TCP \
        --host=127.0.0.1 "--port=$db_port" --user=root --connect-timeout=2 \
        --batch --skip-column-names --default-character-set=utf8mb4 "$@"
}
mysql_args=(--no-defaults "--basedir=$mysql" "--datadir=$data")
if [ ! -f "$runtime/mysql-initialized" ]; then
    [ ! -e "$data" ] || die "An incomplete MySQL initialization exists. Check $logs."
    printf '%s\n' 'Initializing standalone MySQL ...'
    "$mysql/bin/mysqld" "${mysql_args[@]}" --initialize "--log-error=$logs/mysql-initialize.log"
    touch "$runtime/mysql-initialized"
fi
mysql_args+=("--port=$db_port" --bind-address=127.0.0.1 --mysqlx=OFF "--log-error=$logs/mysql.log"
    "--socket=$socket_dir/mysql.sock" "--pid-file=$run/mysql.pid")
if [ ! -f "$runtime/mysql-password-set" ]; then
    printf "ALTER USER 'root'@'localhost' IDENTIFIED BY '%s';\n" "$root_secret" > "$run/mysql-first-start.sql"
    mysql_args+=("--init-file=$run/mysql-first-start.sql")
fi
"$mysql/bin/mysqld" "${mysql_args[@]}" >> "$logs/mysql-console.log" 2>&1 &
mysql_pid=$!
printf '%s\n' 'Waiting for standalone MySQL ...'
ready=false
for ((attempt=0; attempt<45; attempt++)); do
    kill -0 "$mysql_pid" 2>/dev/null || break
    if [ "$(mysql_root --execute='SELECT 1;' 2>/dev/null)" = 1 ]; then ready=true; break; fi
    sleep 1
done
$ready || die "Standalone MySQL did not start. Check $logs."
touch "$runtime/mysql-password-set"
rm -f -- "$run/mysql-first-start.sql"

[ -f "$project/database/01_create_schema.sql" ] || die 'The SQL migration files are missing.'
mysql_root <<'SQL'
CREATE DATABASE IF NOT EXISTS netbanking CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS netbanking.standalone_migrations (
    filename VARCHAR(128) NOT NULL PRIMARY KEY, completed BOOLEAN NOT NULL DEFAULT FALSE
);
SQL
for sql in "$project"/database/[0-9][0-9]_*.sql; do
    name=$(basename -- "$sql")
    [[ $name =~ ^[0-9]{2}_[a-zA-Z0-9_]+\.sql$ ]] || die "Unexpected migration filename: $name."
    status=$(mysql_root --execute="SELECT completed FROM netbanking.standalone_migrations WHERE filename='$name';")
    [ "$status" != 1 ] || continue
    [ "$status" != 0 ] || die "A previous run did not complete $name. Check the standalone database before retrying."
    printf 'Applying %s ...\n' "$name"
    mysql_root --execute="INSERT INTO netbanking.standalone_migrations(filename) VALUES ('$name');"
    mysql_root --database=netbanking < "$sql"
    mysql_root --execute="UPDATE netbanking.standalone_migrations SET completed=TRUE WHERE filename='$name';"
done
mysql_root 2>/dev/null <<SQL || die 'Could not provision the standalone database account.'
CREATE USER IF NOT EXISTS 'proerp'@'127.0.0.1' IDENTIFIED BY '$db_secret';
GRANT SELECT, INSERT, UPDATE, DELETE ON netbanking.* TO 'proerp'@'127.0.0.1';
SQL

webapp="$run/webapps/PROERP"
classes="$webapp/WEB-INF/classes"
lib="$webapp/WEB-INF/lib"
mkdir -p -- "$classes" "$lib" "$run/logs" "$run/temp"
cp -R -- "$tomcat/conf" "$run/"
cp -R -- "$project/WebContent/assets" "$project/WebContent/index.jsp" "$webapp/"
cp -R -- "$project/WebContent/WEB-INF/views" "$project/WebContent/WEB-INF/web.xml" "$webapp/WEB-INF/"
driver="$lib/$driver_name"
cp -- "$connector/$driver_name" "$driver"
java_sources=()
while IFS= read -r -d '' source; do java_sources+=("$source"); done < <(find "$project/src" -name '*.java' -type f -print0)
printf '%s\n' 'Building PROERP with Java 17 ...'
"$JAVA_HOME/bin/javac" --release 17 -encoding UTF-8 -cp "$tomcat/lib/servlet-api.jar:$driver" -d "$classes" "${java_sources[@]}"
cat > "$classes/db.properties" <<EOF
db.driver=com.mysql.cj.jdbc.Driver
db.url=jdbc:mysql://127.0.0.1:$db_port/netbanking?sslMode=DISABLED&allowPublicKeyRetrieval=true
db.username=proerp
db.password=$db_secret
EOF
cat > "$classes/demo.properties" <<EOF
demo.admin.username=proerp.presentation
demo.admin.password=$admin_secret
demo.customer.username=aditi.presentation
demo.customer.password=$customer_secret
EOF
if [ ! -f "$runtime/demo-seeded" ]; then
    "$JAVA_HOME/bin/java" -cp "$classes:$driver" "$project/tools/StandaloneDemoSetup.java"
    touch "$runtime/demo-seeded"
fi

sed -e 's/<Server port="8005" /<Server port="-1" /' \
    -e "s/<Connector port=\"8080\" protocol=\"HTTP\/1.1\"/<Connector port=\"$http_port\" address=\"127.0.0.1\" protocol=\"HTTP\/1.1\"/" \
    "$tomcat/conf/server.xml" > "$run/conf/server.xml"
grep -Fq "port=\"$http_port\" address=\"127.0.0.1\"" "$run/conf/server.xml" || die 'Could not configure the Tomcat HTTP connector.'
grep -Fq '<Server port="-1"' "$run/conf/server.xml" || die 'Could not configure Tomcat shutdown.'
"$JAVA_HOME/bin/java" "-Dcatalina.home=$tomcat" "-Dcatalina.base=$run" "-Djava.io.tmpdir=$run/temp" \
    "-Djava.util.logging.config.file=$run/conf/logging.properties" \
    -Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager \
    -cp "$tomcat/bin/bootstrap.jar:$tomcat/bin/tomcat-juli.jar" \
    org.apache.catalina.startup.Bootstrap start > "$logs/tomcat.log" 2>&1 &
tomcat_pid=$!
url="http://127.0.0.1:$http_port/PROERP/"
ready=false
for ((attempt=0; attempt<45; attempt++)); do
    kill -0 "$tomcat_pid" 2>/dev/null || break
    if curl -fs --noproxy '*' --max-time 2 -o /dev/null "$url"; then ready=true; break; fi
    sleep 1
done
$ready || die "Tomcat did not serve PROERP. Check $logs."
printf 'PROERP is ready at %s\n' "$url"

test_demo_sign_in() {
    local role=$1 heading=$2 login="$url$1/login" cookie="$run/$1.cookies" form token page
    local curl_args=(-fsS --noproxy '*' --max-time 15)
    form=$(curl "${curl_args[@]}" --cookie-jar "$cookie" "$login")
    [[ $form == *'value="demo"'* && $form =~ name=\"_csrf\"\ value=\"([^\"]+)\" ]] ||
        die "The $role demo sign-in form did not render."
    token=${BASH_REMATCH[1]}
    page=$(curl "${curl_args[@]}" --location --cookie "$cookie" \
        --data-urlencode "_csrf=$token" --data action=demo "$login")
    [[ $page == *"$heading"* ]] || die "The $role demo account could not sign in."
    printf 'Verified %s demo sign-in.\n' "$role"
}

if $check; then
    test_demo_sign_in admin 'Operations overview'
    test_demo_sign_in customer 'Welcome, Aditi Menon'
else
    printf '%s\n' 'Use the demo buttons on Admin sign-in and Customer sign-in.'
    printf '%s' 'Press Enter to stop PROERP and its standalone database. '
    read -r unused || true
fi
