<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="description" content="Explore PROERP accounts, transfers, fixed deposits, and loans. Sign in or register for online banking.">
    <title>PROERP Banking | Personal and Digital Banking</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/home.css?v=classic-spacing">
</head>
<body>
<a class="skip" href="#main">Skip to content</a>
<header>
    <div class="wrap header-inner">
        <a class="logo" href="${pageContext.request.contextPath}/">
            <b class="mark" aria-hidden="true">P<span></span></b><span>PROERP<small>BANKING</small></span>
        </a>
        <nav aria-label="Main navigation">
            <a href="#banking">Personal banking</a><a href="#digital">Digital banking</a>
            <a href="#security">Security</a><a href="#about">About us</a>
        </nav>
        <div class="header-sign-ins">
            <a class="button button-outline" href="${pageContext.request.contextPath}/admin/login">Admin sign-in</a>
            <a class="button" href="${pageContext.request.contextPath}/customer/login">Customer sign-in</a>
        </div>
    </div>
</header>
<main id="main">
    <section class="hero">
        <div class="wrap hero-grid">
            <div>
                <p class="eyebrow">PERSONAL BANKING</p>
                <h1>Banking for<br>today and <em>tomorrow.</em></h1>
                <p class="intro">Check your balance, send money, and manage your savings and loans online.</p>
                <div class="actions">
                    <a class="button" href="#banking">Explore banking</a>
                    <a class="text-link" href="${pageContext.request.contextPath}/customer/register">Register for online banking</a>
                </div>
            </div>
            <div class="hero-art" aria-hidden="true">
                <div class="orbit"></div><div class="orbit second"></div>
                <div class="balance">
                    <div class="balance-top"><span>Everyday account</span><span class="tag">Sample</span></div>
                    <small>Available balance</small><strong>₹ 48,250<span>.00</span></strong>
                    <div class="chart"><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i></div>
                    <div class="chart-label"><span>May</span><span>October</span></div>
                </div>
                <div class="bank-card">
                    <div class="card-top"><b>PROERP</b></div>
                    <span class="chip"></span><p>**** &nbsp; **** &nbsp; **** &nbsp; 2048</p>
                    <div class="card-bottom"><span>SAMPLE CARD</span><span>DEBIT</span></div>
                </div>
                <div class="goal"><div><strong>Emergency fund</strong><small>₹ 12,000 of ₹ 20,000 saved</small></div></div>
                <p class="art-caption">Illustration with fictional balances</p>
            </div>
        </div>
    </section>

    <section id="banking" class="section wrap">
        <h2>Explore banking services</h2>
        <div class="services">
            <article class="service"><h3>Everyday banking</h3><p>View balances, download statements, and manage your debit card.</p></article>
            <article class="service"><h3>Payments &amp; transfers</h3><p>Save payees, send money between PROERP accounts, and pay simulated bills.</p></article>
            <article class="service"><h3>Savings &amp; loans</h3><p>Open a fixed deposit, apply for a loan, and track your repayments.</p></article>
        </div>
    </section>

    <section id="digital" class="digital">
        <div class="wrap digital-grid">
            <div class="digital-visual">
                <div class="visual-heading"><b aria-hidden="true">P</b><span>ONLINE BANKING</span></div>
                <div class="digital-line"><strong>Check account balances</strong></div>
                <div class="digital-line"><strong>Manage cards and saved payees</strong></div>
                <div class="digital-line"><strong>Download statements and receipts</strong></div>
            </div>
            <div class="digital-copy">
                <h2>Digital banking</h2>
                <p>Register with your chosen username and password. Once an admin approves your registration, you can sign in with the same credentials.</p>
                <a class="button" href="${pageContext.request.contextPath}/customer/login">Open customer banking</a>
            </div>
        </div>
    </section>

    <section id="security" class="section wrap">
        <h2>Bank safely online</h2>
        <div class="security-grid">
            <article><h3>Protect your sign-in</h3><p>Keep your password and verification codes private.</p></article>
            <article><h3>Check the website address</h3><p>Make sure you’re on the correct site before entering your credentials.</p></article>
            <article><h3>Sign out on shared devices</h3><p>Use the sign-out button before leaving a shared computer.</p></article>
        </div>
    </section>

    <section id="about" class="wrap about">
        <h2>About PROERP Banking</h2>
        <div><p>PROERP is a college project that demonstrates online banking and admin approvals using fictional accounts and balances.</p><a class="text-link" href="${pageContext.request.contextPath}/admin/login">Visit the administrator workspace</a></div>
    </section>

</main>
<footer>
    <div class="wrap footer-top">
        <a class="logo" href="${pageContext.request.contextPath}/"><b class="mark" aria-hidden="true">P<span></span></b><span>PROERP<small>BANKING</small></span></a>
        <nav aria-label="Footer navigation">
            <a href="#banking">Banking</a><a href="#security">Security tips</a>
            <a href="${pageContext.request.contextPath}/admin/login">Admin sign-in</a>
        </nav>
    </div>
    <div class="wrap footer-bottom"><span>&copy; <%= java.time.Year.now() %> PROERP Banking</span><p>Educational banking demo</p></div>
</footer>
</body>
</html>
