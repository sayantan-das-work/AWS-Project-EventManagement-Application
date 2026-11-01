const form = document.getElementById("login-form");
const button = document.getElementById("submit");
const errorEl = document.getElementById("error");

// Already signed in? Skip the form.
if (sessionStorage.getItem("tokens")) {
  window.location.replace("welcome.html");
}

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  errorEl.textContent = "";

  const username = document.getElementById("username").value.trim();
  const password = document.getElementById("password").value;
  if (!username || !password) {
    errorEl.textContent = "Enter your email and password.";
    return;
  }

  button.disabled = true;
  button.textContent = "Signing in…";

  try {
    const res = await fetch(`${window.APP_CONFIG.API_URL}/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password }),
    });
    const data = await res.json().catch(() => ({}));

    if (!res.ok) {
      errorEl.textContent = data.challenge
        ? `Extra step required: ${data.challenge}`
        : data.message || "Sign in failed.";
      return;
    }

    // sessionStorage is cleared when the tab closes. For production, prefer an
    // HttpOnly cookie for the refresh token.
    sessionStorage.setItem("tokens", JSON.stringify(data));
    window.location.href = "welcome.html";
  } catch (err) {
    errorEl.textContent = "Cannot reach the server. Check API_URL and CORS.";
  } finally {
    button.disabled = false;
    button.textContent = "Sign in";
  }
});
