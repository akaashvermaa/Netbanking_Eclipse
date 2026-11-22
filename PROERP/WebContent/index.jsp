<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="description" content="Explore everyday banking, savings, digital services, and security at PROERP Banking.">
    <title>PROERP Banking | Personal and Digital Banking</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/home.css">
</head>
<body>
<a class="skip" href="#main">Skip to content</a>
<div class="utility"><div class="wrap"><span>PROERP educational banking demo</span><a href="#help">Help &amp; FAQs</a></div></div>
<header>
    <div class="wrap header-inner">
        <a class="logo" href="${pageContext.request.contextPath}/">
            <b class="mark" aria-hidden="true">P<span></span></b><span>PROERP<small>BANKING</small></span>
        </a>
        <nav aria-label="Main navigation">
            <a href="#banking">Personal banking</a><a href="#digital">Digital banking</a>
            <a href="#security">Security</a><a href="#about">About us</a>
        </nav>
        <a class="button small" href="${pageContext.request.contextPath}/admin/login">Admin sign-in</a>
    </div>
</header>
<main id="main">
    <section class="hero">
        <div class="wrap hero-grid">
            <div>
                <p class="eyebrow">PERSONAL BANKING</p>
                <h1>Banking for<br>today and <em>tomorrow.</em></h1>
                <p class="intro">Manage everyday spending, build savings habits, and learn how digital banking can help you keep track of your money.</p>
                <div class="actions">
                    <a class="button" href="#banking">Explore banking</a>
                    <a class="text-link" href="#digital">Discover digital banking</a>
                </div>
            </div>
            <div class="hero-art" aria-hidden="true">
                <div class="orbit"></div><div class="orbit second"></div>
                <div class="balance">
                    <div class="balance-top"><span>Everyday account</span><span class="tag">ILLUSTRATION</span></div>
                    <small>Sample balance</small><strong>₹ 48,250<span>.00</span></strong>
                    <div class="chart"><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i></div>
                    <div class="chart-label"><span>May</span><span>October</span></div>
                </div>
                <div class="bank-card">
                    <div class="card-top"><b>PROERP</b><span>)))</span></div>
                    <span class="chip"></span><p>**** &nbsp; **** &nbsp; **** &nbsp; 2048</p>
                    <div class="card-bottom"><span>SAMPLE CARD</span><span>DEBIT</span></div>
                </div>
                <div class="goal"><div><strong>Emergency fund</strong><small>₹ 12,000 of ₹ 20,000 saved</small></div></div>
                <p class="art-caption">Illustration with fictional data</p>
            </div>
        </div>
    </section>

    <section id="banking" class="section wrap">
        <div class="section-heading"><h2>Explore banking services</h2><p>Learn what different kinds of banking are for and how they fit into everyday life.</p></div>
        <div class="services">
            <article class="service"><h3>Everyday banking</h3><p>Use an account to receive money, pay bills, and keep track of regular spending.</p><a href="#everyday-info">Learn about everyday accounts</a></article>
            <article class="service"><h3>Savings &amp; goals</h3><p>Set money aside for an emergency fund, a planned purchase, or a longer term goal.</p><a href="#savings-info">Plan a savings goal</a></article>
            <article class="service"><h3>Business banking</h3><p>Keep business income and expenses separate so you can see how money moves through your business.</p><a href="#business-info">Explore business accounts</a></article>
        </div>
    </section>

    <section id="digital" class="digital">
        <div class="wrap digital-grid">
            <div class="digital-visual">
                <div class="visual-heading"><b aria-hidden="true">P.</b><span>PLANNED CUSTOMER FEATURES</span></div>
                <div class="digital-line"><strong>Open an account</strong></div>
                <div class="digital-line"><strong>Transfer money between accounts</strong></div>
                <div class="digital-line"><strong>View transactions and statements</strong></div>
            </div>
            <div><h2>Digital banking</h2><p>An online banking portal lets you see your balance, check recent payments, and browse statements from your own device.</p><p>Customer banking is planned for a later release of this demo. The administrator workspace is available now.</p><a class="button" href="#help">See what’s available</a></div>
        </div>
    </section>

    <section id="security" class="section wrap">
        <h2>Bank safely online</h2>
        <div class="security-grid">
            <article><h3>Keep credentials private</h3><p>Keep passwords, PINs, and one-time codes to yourself, especially if someone contacts you unexpectedly.</p></article>
            <article><h3>Check the website address</h3><p>Open your bank using a trusted bookmark or its official app before entering your account details.</p></article>
            <article><h3>Sign out when you finish</h3><p>End your banking session on shared devices and keep your browser up to date.</p></article>
        </div>
    </section>

    <section id="about" class="wrap about">
        <h2>About PROERP Banking</h2>
        <div><p>PROERP is a college project that demonstrates how a net banking platform works. The admin team can review customer registrations, manage access, and track decisions in the audit history.</p><a class="text-link" href="${pageContext.request.contextPath}/admin/login">Visit the administrator workspace</a></div>
    </section>

    <section id="help" class="section wrap help">
        <h2>Banking FAQs</h2>
        <div class="faq">
            <details id="everyday-info"><summary>What is an everyday account for?</summary><p>An everyday account is used for regular transactions, such as receiving salary, paying bills, and reviewing purchases.</p></details>
            <details id="savings-info"><summary>How can I plan a savings goal?</summary><p>Choose an amount and a target date. Break the total into regular contributions and track what you set aside.</p></details>
            <details id="business-info"><summary>Why use a separate business account?</summary><p>It gives you a separate record of business income and expenses, which helps you track cash flow.</p></details>
            <details><summary>Can I open an account or transfer money here?</summary><p>Customer account opening and transfers are planned. This educational demo uses fictional data.</p></details>
        </div>
    </section>
</main>
<footer>
    <div class="wrap footer-top">
        <a class="logo" href="${pageContext.request.contextPath}/"><b class="mark" aria-hidden="true">P<span></span></b><span>PROERP<small>BANKING</small></span></a>
        <nav aria-label="Footer navigation">
            <a href="#banking">Banking</a><a href="#security">Security tips</a>
            <a href="#help">Help &amp; FAQs</a><a href="${pageContext.request.contextPath}/admin/login">Admin sign-in</a>
        </nav>
    </div>
    <div class="wrap footer-bottom"><span>&copy; <%= java.time.Year.now() %> PROERP Banking</span><p>College demonstration using fictional accounts and balances.</p></div>
</footer>
<script>
    for (const link of document.querySelectorAll('a[href$="-info"]')) {
        const answer = document.querySelector(link.hash);
        link.addEventListener('click', () => { answer.open = true; });
        if (location.hash === link.hash) answer.open = true;
    }
</script>
</body>
</html>
