[CmdletBinding()]
param(
    [ValidateRange(1024,65535)][int]$HttpPort = 8081,
    [ValidateRange(1024,65535)][int]$DbPort = 3307,
    [switch]$Check
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$runtime = Join-Path $project '.standalone'
$downloads = Join-Path $runtime 'downloads'
$logs = Join-Path $runtime 'logs'
$data = Join-Path $runtime 'mysql-data'
$utf8 = [Text.UTF8Encoding]::new($false)

function Write-LocalFile($path, $content) {
    [IO.File]::WriteAllText($path, $content, $utf8)
}

function Assert-RuntimeChild($path) {
    $prefix = [IO.Path]::GetFullPath($runtime).TrimEnd('\') + '\'
    $resolved = (Resolve-Path -LiteralPath $path).ProviderPath
    if (!$resolved.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase) -or
            ((Get-Item -LiteralPath $resolved -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw "Unsafe generated path: $path"
    }
}

function Test-ArchiveChecksum($path, $algorithm, $checksum) {
    if (!(Test-Path -LiteralPath $path -PathType Leaf)) { return $false }
    $digest = [Security.Cryptography.HashAlgorithm]::Create($algorithm)
    try {
        $file = [IO.File]::OpenRead($path)
        try { return [BitConverter]::ToString($digest.ComputeHash($file)).Replace('-', '') -eq $checksum }
        finally { $file.Dispose() }
    } finally { $digest.Dispose() }
}

function Get-RuntimeArchive($name, $urls, $algorithm, $checksum, $sentinel) {
    $installed = Join-Path $runtime ($name -replace '\.zip$', '')
    if (Test-Path -LiteralPath (Join-Path $installed $sentinel) -PathType Leaf) { return $installed }
    $archive = Join-Path $downloads $name
    if (!(Test-ArchiveChecksum $archive $algorithm $checksum)) {
        Write-Host "Downloading $name ..."
        $partial = $archive + '.part'
        $validated = $false
        foreach ($url in $urls) {
            & curl.exe --fail --silent --show-error --location --proto '=https' --proto-redir '=https' --retry 2 --max-time 600 -o $partial $url
            if ($LASTEXITCODE -eq 0 -and (Test-ArchiveChecksum $partial $algorithm $checksum)) {
                Move-Item -LiteralPath $partial -Destination $archive -Force
                $validated = $true
                break
            }
        }
        if (!$validated) { throw "Download or checksum verification failed for $name." }
    }
    Write-Host "Extracting $name ..."
    $unpack = Join-Path $runtime ('extract-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $unpack | Out-Null
    try {
        & tar.exe -xf $archive -C $unpack
        if ($LASTEXITCODE -ne 0) { throw "Could not extract $name." }
        $matches = @(Get-ChildItem -LiteralPath $unpack -Directory | Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName $sentinel) })
        if ($matches.Count -ne 1 -or (Test-Path -LiteralPath $installed)) { throw "Unexpected archive layout for $name." }
        Assert-RuntimeChild $matches[0].FullName
        Move-Item -LiteralPath $matches[0].FullName -Destination $installed
    } finally {
        Assert-RuntimeChild $unpack
        Remove-Item -LiteralPath $unpack -Recurse -Force
    }
    return $installed
}

function Join-NativeArguments([string[]]$arguments) {
    return (($arguments | ForEach-Object { '"' + ($_ -replace '(\\+)$', '$1$1') + '"' }) -join ' ')
}

function Assert-PortAvailable($port) {
    $probe = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $port)
    $probe.ExclusiveAddressUse = $true
    try { $probe.Start() }
    catch { throw "Port $port is in use. Close the other PROERP window or pass a different port." }
    finally { $probe.Stop() }
}

function Get-LocalSecrets {
    $file = Join-Path $runtime 'local-secrets.json'
    if (!(Test-Path -LiteralPath $file)) {
        if (Test-Path -LiteralPath $data) { throw 'The standalone database already exists but its local-secrets.json is missing.' }
        $newSecrets = [ordered]@{}
        foreach ($key in @('root', 'database', 'admin', 'customer')) {
            $newSecrets[$key] = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
        }
        Write-LocalFile $file ($newSecrets | ConvertTo-Json)
    }
    $saved = Get-Content -LiteralPath $file -Raw | ConvertFrom-Json
    foreach ($key in @('root', 'database', 'admin', 'customer')) {
        if ($saved.$key -notmatch '^[a-f0-9]{64}$') { throw 'The standalone credential file is invalid.' }
    }
    return $saved
}

function Invoke-MySql($statement, [switch]$Quiet, [switch]$Sensitive) {
    $settings = [Diagnostics.ProcessStartInfo]::new()
    $settings.FileName = Join-Path $mysql 'bin/mysql.exe'
    $settings.Arguments = Join-NativeArguments @("--defaults-file=$rootOptions", '--no-login-paths', '--protocol=TCP',
        '--host=127.0.0.1', "--port=$DbPort", '--user=root', '--connect-timeout=2',
        '--batch', '--skip-column-names', '--default-character-set=utf8mb4')
    $settings.UseShellExecute = $false
    $settings.CreateNoWindow = $true
    $settings.RedirectStandardInput = $true
    $settings.RedirectStandardOutput = $true
    $settings.RedirectStandardError = $true
    $process = [Diagnostics.Process]::Start($settings)
    try {
        $readOutput = $process.StandardOutput.ReadToEndAsync()
        $readError = $process.StandardError.ReadToEndAsync()
        $inputBytes = $utf8.GetBytes($statement + [Environment]::NewLine)
        $stream = $process.StandardInput.BaseStream
        try { $stream.Write($inputBytes, 0, $inputBytes.Length) }
        catch [IO.IOException] { }
        finally { $stream.Dispose() }
        if (!$process.WaitForExit(45000)) {
            $process.Kill()
            throw 'The standalone MySQL client timed out.'
        }
        $output = $readOutput.GetAwaiter().GetResult()
        $errorText = $readError.GetAwaiter().GetResult().Trim()
        if ($process.ExitCode -eq 0) { return $output.Trim() }
        if ($Quiet) { return $null }
        if ($Sensitive) { throw 'Could not provision the standalone database account.' }
        throw "MySQL failed: $errorText"
    } finally {
        $process.Dispose()
    }
}

function Update-Database($secrets) {
    $scripts = @(Get-ChildItem -LiteralPath (Join-Path $project 'database') -File -Filter '*.sql' |
        Where-Object Name -match '^\d{2}_[A-Za-z0-9_]+\.sql$' | Sort-Object Name)
    if ($scripts.Count -eq 0 -or $scripts[0].Name -ne '01_create_schema.sql') { throw 'The project migration files are missing.' }
    $null = Invoke-MySql @'
CREATE DATABASE IF NOT EXISTS netbanking CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS netbanking.standalone_migrations (
    filename VARCHAR(128) NOT NULL PRIMARY KEY, completed BOOLEAN NOT NULL DEFAULT FALSE
);
'@
    foreach ($script in $scripts) {
        $status = Invoke-MySql "SELECT completed FROM netbanking.standalone_migrations WHERE filename='$($script.Name)';"
        if ($status -eq '1') { continue }
        if ($status -eq '0') { throw "A previous run did not complete $($script.Name). Check the standalone database before retrying this migration." }
        Write-Host "Applying $($script.Name) ..."
        $null = Invoke-MySql "INSERT INTO netbanking.standalone_migrations(filename) VALUES ('$($script.Name)');"
        $sql = [IO.File]::ReadAllText($script.FullName)
        $null = Invoke-MySql ("USE netbanking;" + [Environment]::NewLine + $sql)
        $null = Invoke-MySql "UPDATE netbanking.standalone_migrations SET completed=TRUE WHERE filename='$($script.Name)';"
    }
    $null = Invoke-MySql -Sensitive @"
CREATE USER IF NOT EXISTS 'proerp'@'127.0.0.1' IDENTIFIED BY '$($secrets.database)';
GRANT SELECT, INSERT, UPDATE, DELETE ON netbanking.* TO 'proerp'@'127.0.0.1';
"@
}

function Test-DemoSignIn($url, $role, $heading, $cookieFile) {
    $loginUrl = "$url$role/login"
    $http = @('--fail', '--silent', '--show-error', '--noproxy', '*', '--max-time', '15')
    $form = (& curl.exe @http --cookie-jar $cookieFile $loginUrl) -join [Environment]::NewLine
    if ($LASTEXITCODE -ne 0 -or !$form.Contains('value="demo"')) { throw "The $role demo sign-in form did not render." }
    $token = [regex]::Match($form, 'name="_csrf" value="([^"]+)"').Groups[1].Value
    if (!$token) { throw "The $role sign-in form did not return a form token." }
    $page = (& curl.exe @http --location --cookie $cookieFile --cookie-jar $cookieFile --data-urlencode "_csrf=$token" --data 'action=demo' $loginUrl) -join [Environment]::NewLine
    if ($LASTEXITCODE -ne 0 -or !$page.Contains($heading)) { throw "The $role demo account could not sign in." }
    Write-Host "Verified $role demo sign-in."
}

if ($env:PROCESSOR_ARCHITECTURE -ne 'AMD64') { throw 'Run the launcher with 64-bit Windows PowerShell on Windows x64.' }
if ($HttpPort -eq $DbPort) { throw 'Choose different HTTP and MySQL ports.' }
New-Item -ItemType Directory -Path $runtime, $downloads, $logs -Force | Out-Null
if ((Get-Item -LiteralPath $runtime -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'The standalone folder must be a local directory.' }
try {
    $lock = [IO.File]::Open((Join-Path $runtime 'run.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
} catch [IO.IOException] {
    throw 'This copy of PROERP is already being set up or running.'
}
$mysqlProcess = $null
$tomcatProcess = $null
$run = $null
try {
    Assert-PortAvailable $HttpPort
    Assert-PortAvailable $DbPort
    $jdk = Get-RuntimeArchive 'microsoft-jdk-17.0.21.1-windows-x64.zip' @(
        'https://aka.ms/download-jdk/microsoft-jdk-17.0.21.1-windows-x64.zip'
    ) SHA256 'A07E270F86AEC056D9C2ACF981EB64A38E30814DACFADBC5F80F31C55D5A4427' 'bin/javac.exe'
    $tomcat = Get-RuntimeArchive 'apache-tomcat-10.1.62.zip' @(
        'https://downloads.apache.org/tomcat/tomcat-10/v10.1.62/bin/apache-tomcat-10.1.62.zip',
        'https://archive.apache.org/dist/tomcat/tomcat-10/v10.1.62/bin/apache-tomcat-10.1.62.zip'
    ) SHA512 '43198B4BD591A681B9EFFB7795DE3F7B07ECC2B2D8C0158A1F8BCF0182BDA4F6CA566C61E49371D81D6DCC73B518E18AAA711D5498B05FCC617659E45D96DB1E' 'bin/bootstrap.jar'
    $mysql = Get-RuntimeArchive 'mysql-8.4.13-winx64.zip' @(
        'https://cdn.mysql.com/Downloads/MySQL-8.4/mysql-8.4.13-winx64.zip',
        'https://cdn.mysql.com/archives/mysql-8.4/mysql-8.4.13-winx64.zip'
    ) SHA256 'E9CD98B1D23F122987B1AFCA26AD942EFEC5891481103D48AE3CDE237FA3EA54' 'bin/mysqld.exe'
    $driverName = 'mysql-connector-j-26.10.0.jar'
    $connector = Get-RuntimeArchive 'mysql-connector-j-26.10.0.zip' @(
        'https://cdn.mysql.com/Downloads/Connector-J/mysql-connector-j-26.10.0.zip',
        'https://cdn.mysql.com/archives/mysql-connector-java-26.10/mysql-connector-j-26.10.0.zip'
    ) SHA256 '01A5A26CC5EF52A55301388CABF70E3F44F84DE80EA162BF33A0B80F2E181E82' $driverName

    $secrets = Get-LocalSecrets
    $rootOptions = Join-Path $runtime 'mysql-root.cnf'
    Write-LocalFile $rootOptions "[client]
password=$($secrets.root)
"
    $server = Join-Path $mysql 'bin/mysqld.exe'
    $mysqlArgs = @('--no-defaults', "--basedir=$mysql", "--datadir=$data")
    $initialized = Join-Path $runtime 'mysql-initialized'
    $passwordSet = Join-Path $runtime 'mysql-password-set'
    if (!(Test-Path -LiteralPath $initialized)) {
        if (Test-Path -LiteralPath $data) { throw "An incomplete MySQL initialization exists at $data. Check the standalone logs." }
        Write-Host 'Initializing standalone MySQL ...'
        $argsForInit = @($mysqlArgs) + @('--initialize', "--log-error=$(Join-Path $logs 'mysql-initialize.log')")
        $initial = Start-Process -FilePath $server -ArgumentList (Join-NativeArguments $argsForInit) -PassThru -Wait -WindowStyle Hidden
        if ($initial.ExitCode -ne 0) { throw "MySQL initialization failed. Check $logs and the Microsoft Visual C++ x64 runtime." }
        Write-LocalFile $initialized 'initialized'
    }

    $mysqlArgs += @("--port=$DbPort", '--bind-address=127.0.0.1', '--mysqlx=OFF', "--log-error=$(Join-Path $logs 'mysql.log')")
    $initFile = Join-Path $runtime 'mysql-first-start.sql'
    if (!(Test-Path -LiteralPath $passwordSet)) {
        Write-LocalFile $initFile "ALTER USER 'root'@'localhost' IDENTIFIED BY '$($secrets.root)';"
        $mysqlArgs += "--init-file=$initFile"
    }
    $mysqlProcess = Start-Process -FilePath $server -ArgumentList (Join-NativeArguments $mysqlArgs) -PassThru -WindowStyle Hidden
    Write-Host 'Waiting for standalone MySQL ...'
    $ready = $false
    for ($attempt = 0; $attempt -lt 45 -and !$mysqlProcess.HasExited; $attempt++) {
        if ((Invoke-MySql 'SELECT 1;' -Quiet) -eq '1') { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (!$ready) { throw "Standalone MySQL did not start. Check $logs." }
    Write-LocalFile $passwordSet 'configured'
    if (Test-Path -LiteralPath $initFile) { Remove-Item -LiteralPath $initFile }
    Update-Database $secrets

    $run = Join-Path $runtime ('run-' + [guid]::NewGuid().ToString('N'))
    $webapp = Join-Path $run 'webapps/PROERP'
    $classes = Join-Path $webapp 'WEB-INF/classes'
    $lib = Join-Path $webapp 'WEB-INF/lib'
    New-Item -ItemType Directory -Path $classes, $lib, (Join-Path $run 'logs'), (Join-Path $run 'temp') -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $tomcat 'conf') -Destination $run -Recurse
    $webSource = Join-Path $project 'WebContent'
    Get-ChildItem -LiteralPath $webSource | Where-Object Name -ne 'WEB-INF' |
        ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination $webapp -Recurse }
    foreach ($item in @('web.xml', 'views')) {
        Copy-Item -LiteralPath (Join-Path $webSource "WEB-INF/$item") -Destination (Join-Path $webapp 'WEB-INF') -Recurse
    }
    Copy-Item -LiteralPath (Join-Path $connector $driverName) -Destination $lib
    $driver = Join-Path $lib $driverName
    $javac = Join-Path $jdk 'bin/javac.exe'
    $java = Join-Path $jdk 'bin/java.exe'
    $servlets = Join-Path $tomcat 'lib/servlet-api.jar'
    $javaSources = @(Get-ChildItem -LiteralPath (Join-Path $project 'src') -Recurse -Filter '*.java' -File)
    $sourceFiles = @($javaSources | ForEach-Object { $_.FullName.Substring($project.Length + 1) })
    Write-Host 'Building PROERP with Java 17 ...'
    Push-Location -LiteralPath $project
    try {
        & $javac --release 17 -encoding UTF-8 -cp "$servlets;$driver" -d $classes @sourceFiles
        if ($LASTEXITCODE -ne 0) { throw 'The PROERP Java build failed.' }
    } finally { Pop-Location }
    Write-LocalFile (Join-Path $classes 'db.properties') "db.driver=com.mysql.cj.jdbc.Driver
db.url=jdbc:mysql://127.0.0.1:$DbPort/netbanking?sslMode=DISABLED&allowPublicKeyRetrieval=true
db.username=proerp
db.password=$($secrets.database)
"
    Write-LocalFile (Join-Path $classes 'demo.properties') "demo.admin.username=proerp.presentation
demo.admin.password=$($secrets.admin)
demo.customer.username=aditi.presentation
demo.customer.password=$($secrets.customer)
"
    $seeded = Join-Path $runtime 'demo-seeded'
    if (!(Test-Path -LiteralPath $seeded)) {
        & $java -cp "$classes;$driver" (Join-Path $PSScriptRoot 'StandaloneDemoSetup.java')
        if ($LASTEXITCODE -ne 0) { throw 'Fictional demo setup did not finish.' }
        Write-LocalFile $seeded 'seeded'
    }

    $shutdown = [guid]::NewGuid().ToString('N')
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { $stopPort = $listener.LocalEndpoint.Port } finally { $listener.Stop() }
    $serverXml = Join-Path $run 'conf/server.xml'
    [xml]$config = Get-Content -LiteralPath $serverXml -Raw
    $config.Server.SetAttribute('port', [string]$stopPort)
    $config.Server.SetAttribute('address', '127.0.0.1')
    $config.Server.SetAttribute('shutdown', $shutdown)
    $http = $config.SelectSingleNode('/Server/Service/Connector[@port="8080"]')
    $http.SetAttribute('port', [string]$HttpPort)
    $http.SetAttribute('address', '127.0.0.1')
    $config.Save($serverXml)

    $bootstrap = "$(Join-Path $tomcat 'bin/bootstrap.jar');$(Join-Path $tomcat 'bin/tomcat-juli.jar')"
    $javaArgs = @("-Dcatalina.home=$tomcat", "-Dcatalina.base=$run", "-Djava.io.tmpdir=$(Join-Path $run 'temp')",
        "-Djava.util.logging.config.file=$(Join-Path $run 'conf/logging.properties')",
        '-Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager',
        '-cp', $bootstrap, 'org.apache.catalina.startup.Bootstrap', 'start')
    $tomcatOptions = @{
        FilePath = $java
        ArgumentList = Join-NativeArguments $javaArgs
        PassThru = $true
        WindowStyle = 'Hidden'
        RedirectStandardOutput = Join-Path $logs 'tomcat-output.log'
        RedirectStandardError = Join-Path $logs 'tomcat-error.log'
    }
    $tomcatProcess = Start-Process @tomcatOptions
    $url = "http://127.0.0.1:$HttpPort/PROERP/"
    $ready = $false
    for ($attempt = 0; $attempt -lt 45 -and !$tomcatProcess.HasExited; $attempt++) {
        & curl.exe -fs --noproxy '*' --max-time 2 -o NUL $url
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (!$ready) { throw "Tomcat did not serve PROERP. Check $logs." }
    Write-Host "PROERP is ready at $url"
    Write-Host 'Use the demo buttons on Admin sign-in and Customer sign-in.'
    if ($Check) {
        Test-DemoSignIn $url 'admin' 'Operations overview' (Join-Path $run 'admin.cookies')
        Test-DemoSignIn $url 'customer' 'Welcome, Aditi Menon' (Join-Path $run 'customer.cookies')
    } else {
        $null = Read-Host 'Press Enter to stop PROERP and its standalone database'
    }
} finally {
    try {
        if ($tomcatProcess -and !$tomcatProcess.HasExited) {
            try {
                $client = [Net.Sockets.TcpClient]::new('127.0.0.1', $stopPort)
                try {
                    $bytes = [Text.Encoding]::ASCII.GetBytes($shutdown)
                    $client.GetStream().Write($bytes, 0, $bytes.Length)
                } finally { $client.Dispose() }
            } catch [Net.Sockets.SocketException] { }
            if (!$tomcatProcess.WaitForExit(12000)) { $tomcatProcess.Kill(); $tomcatProcess.WaitForExit() }
        }
        if ($mysqlProcess -and !$mysqlProcess.HasExited) {
            $null = Invoke-MySql 'SHUTDOWN;' -Quiet
            if (!$mysqlProcess.WaitForExit(12000)) {
                Write-Warning 'MySQL did not shut down cleanly. Check the standalone logs before the next run.'
            }
        }
        if ($run -and (Test-Path -LiteralPath $run) -and (!$tomcatProcess -or $tomcatProcess.HasExited)) {
            Assert-RuntimeChild $run
            Remove-Item -LiteralPath $run -Recurse -Force
        }
    } finally { $lock.Dispose() }
}
