const tokens = JSON.parse(sessionStorage.getItem("tokens") || "null");
if (!tokens || !tokens.idToken) {
  window.location.replace("index.html");
}

// Decode only to display; the API's JWT authorizer does the real verification.
function decodeJwt(jwt) {
  const payload = jwt.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
  const json = decodeURIComponent(
    atob(payload)
      .split("")
      .map((c) => "%" + c.charCodeAt(0).toString(16).padStart(2, "0"))
      .join("")
  );
  return JSON.parse(json);
}

function fillChips(id, items, cls = "") {
  const el = document.getElementById(id);
  if (!items.length) {
    el.innerHTML = '<span class="none">None</span>';
    return;
  }
  for (const item of items) {
    const chip = document.createElement("span");
    chip.className = `chip ${cls}`;
    chip.textContent = item; // textContent: never inject token data as HTML
    el.appendChild(chip);
  }
}

if (tokens) {
  const id = decodeJwt(tokens.idToken);
  const access = decodeJwt(tokens.accessToken);

  const email = id.email || id["cognito:username"] || "";
  const name = id.name || email.split("@")[0] || "friend";

  document.getElementById("name").textContent = name;
  document.getElementById("email").textContent = email;
  document.getElementById("avatar").textContent = name.charAt(0).toUpperCase();

  fillChips("groups", id["cognito:groups"] || access["cognito:groups"] || []);
  // scopes are space separated in the access token; hide the built-in ones
  const scopes = (access.scope || "").split(" ").filter(Boolean);
  fillChips("scopes", scopes.map((s) => s.replace("event-management-application/", "")), "scope");
}

const logoutButton = document.getElementById("logout");
const logoutError = document.getElementById("logout-error");

logoutButton.addEventListener("click", async () => {
  logoutError.textContent = "";
  logoutButton.disabled = true;

  try {
    const res = await fetch(`${window.APP_CONFIG.API_URL}/auth/logout`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken: tokens.refreshToken }),
    });
    if (!res.ok) throw new Error(`logout failed: ${res.status}`);
  } catch (_) {
    // Stay signed in and on this page: the goodbye page is only for a successful logout.
    logoutError.textContent = "Sign out failed. Please try again.";
    logoutButton.disabled = false;
    return;
  }

  sessionStorage.removeItem("tokens");
  sessionStorage.setItem("loggedOut", "1"); // one-time pass for goodbye.html
  window.location.href = "goodbye.html";
});
