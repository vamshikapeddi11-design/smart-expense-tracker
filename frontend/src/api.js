// ---------------------------------------------------------------------------
// Central API helper for the Smart Expense Tracker frontend.
// ---------------------------------------------------------------------------

export const API_URL =
  import.meta.env.VITE_API_URL || "http://localhost:8080";

export const ML_URL =
  import.meta.env.VITE_ML_URL || "http://localhost:8000";

const TOKEN_KEY = "set_token";

// Shared categories
export const CATEGORIES = [
  "Food",
  "Transport",
  "Shopping",
  "Utilities",
  "Rent",
  "Subscriptions",
  "Health",
  "Entertainment",
  "Education",
  "Travel",
  "Other",
];

export const PAYMENT_MODES = [
  "UPI",
  "Cash",
  "Card",
  "NetBanking",
  "Wallet",
];

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token) {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token);
  }
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem("set_user");
}

export function saveUser(user) {
  localStorage.setItem("set_user", JSON.stringify(user));
}

export function getUser() {
  try {
    return JSON.parse(localStorage.getItem("set_user"));
  } catch {
    return null;
  }
}

function authHeaders(extra = {}) {
  const headers = { ...extra };
  const token = getToken();

  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  return headers;
}

// ---------------------------------------------------------------------------
// Low-level fetch wrapper
// ---------------------------------------------------------------------------

async function request(
  base,
  path,
  { method = "GET", body, headers = {} } = {}
) {
  const res = await fetch(`${base}${path}`, {
    method,

    headers: authHeaders(
      body && !(body instanceof FormData)
        ? {
            "Content-Type": "application/json",
            ...headers,
          }
        : headers
    ),

    body:
      body instanceof FormData
        ? body
        : body
          ? JSON.stringify(body)
          : undefined,
  });

  if (!res.ok) {
    let message = `Request failed (${res.status})`;

    try {
      const data = await res.json();
      message =
        data.message ||
        data.error ||
        message;
    } catch {
      // Keep generic message if response is not JSON.
    }

    const err = new Error(message);
    err.status = res.status;
    throw err;
  }

  const text = await res.text();

  return text ? JSON.parse(text) : null;
}

// ---------------------------------------------------------------------------
// Backend - Spring Boot
// ---------------------------------------------------------------------------

export const api = {

  // -------------------------------------------------------------------------
  // Authentication
  // -------------------------------------------------------------------------

  signup: (data) =>
    request(API_URL, "/api/auth/signup", {
      method: "POST",
      body: data,
    }),

  login: (data) =>
    request(API_URL, "/api/auth/login", {
      method: "POST",
      body: data,
    }),

  // -------------------------------------------------------------------------
  // Expenses
  // -------------------------------------------------------------------------

  listExpenses: (params = {}) => {
    const q = new URLSearchParams(params).toString();

    return request(
      API_URL,
      `/api/expenses${q ? `?${q}` : ""}`
    );
  },

  createExpense: (data) =>
    request(API_URL, "/api/expenses", {
      method: "POST",
      body: data,
    }),

  updateExpense: (id, data) =>
    request(API_URL, `/api/expenses/${id}`, {
      method: "PUT",
      body: data,
    }),

  deleteExpense: (id) =>
    request(API_URL, `/api/expenses/${id}`, {
      method: "DELETE",
    }),

  // Correct category
  correctCategory: (id, category) =>
    request(API_URL, `/api/expenses/${id}/correct`, {
      method: "POST",
      body: { category },
    }),

  // Expense summary
  summary: (params) => {
    const q = new URLSearchParams(params).toString();

    return request(
      API_URL,
      `/api/expenses/summary${q ? `?${q}` : ""}`
    );
  },

  // -------------------------------------------------------------------------
  // Budgets
  // -------------------------------------------------------------------------

  listBudgets: () =>
    request(API_URL, "/api/budgets"),

  createBudget: (data) =>
    request(API_URL, "/api/budgets", {
      method: "POST",
      body: data,
    }),

  updateBudget: (id, data) =>
    request(API_URL, `/api/budgets/${id}`, {
      method: "PUT",
      body: data,
    }),

  deleteBudget: (id) =>
    request(API_URL, `/api/budgets/${id}`, {
      method: "DELETE",
    }),

  budgetAlerts: () =>
    request(API_URL, "/api/budgets/alerts"),

  // -------------------------------------------------------------------------
  // Recurring expenses
  // -------------------------------------------------------------------------

  listRecurring: () =>
    request(API_URL, "/api/recurring"),

  createRecurring: (data) =>
    request(API_URL, "/api/recurring", {
      method: "POST",
      body: data,
    }),

  updateRecurring: (id, data) =>
    request(API_URL, `/api/recurring/${id}`, {
      method: "PUT",
      body: data,
    }),

  deleteRecurring: (id) =>
    request(API_URL, `/api/recurring/${id}`, {
      method: "DELETE",
    }),

  // -------------------------------------------------------------------------
  // UPI QR
  // -------------------------------------------------------------------------

  parseQr: (payload) =>
    request(API_URL, "/api/qr/parse", {
      method: "POST",
      body: { payload },
    }),

  scanQrImage: (file) => {
    const form = new FormData();

    form.append("file", file);

    return request(API_URL, "/api/qr/scan-image", {
      method: "POST",
      body: form,
    });
  },

  // -------------------------------------------------------------------------
  // MOCK BANK
  // -------------------------------------------------------------------------

  // Links the Mock Bank account.
  // The backend /api/bank/consent expects a bankName in the request body.
  bankConsent: () =>
    request(API_URL, "/api/bank/consent", {
      method: "POST",
      body: {
        bankName: "Mock Bank",
      },
    }),

  // Gets linked bank accounts
  bankAccounts: () =>
    request(API_URL, "/api/bank/accounts"),

  // Syncs transactions from Mock Bank
  bankSync: () =>
    request(API_URL, "/api/bank/sync", {
      method: "POST",
    }),

  // Imports CSV/XLSX/PDF bank statements
  bankImport: (file) => {
    const form = new FormData();

    form.append("file", file);

    return request(API_URL, "/api/bank/import", {
      method: "POST",
      body: form,
    });
  },
};

// ---------------------------------------------------------------------------
// ML Service - FastAPI
// ---------------------------------------------------------------------------

export const ml = {

  // Categorization
  categorize: (payload) =>
    request(ML_URL, "/categorize", {
      method: "POST",
      body: payload,
    }),

  // Financial insights
  insights: (payload) =>
    request(ML_URL, "/insights", {
      method: "POST",
      body: payload,
    }),

  // Forecast
  forecast: (payload) =>
    request(ML_URL, "/forecast", {
      method: "POST",
      body: payload,
    }),
};