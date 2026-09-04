# 🎨 OpenBounty — Frontend Web Client Portal

This folder contains the web frontend client for **OpenBounty**, dedicated to the Client and Developer / Solver marketplace experience.

---

## 🚀 Quick Setup & Initialization

You can initialize this directory with your framework of choice:

### Option A: Next.js (App Router + Tailwind CSS)
```bash
cd frontend
npx create-next-app@latest . --typescript --tailwind --eslint --app
```

### Option B: React + Vite + Tailwind CSS
```bash
cd frontend
npm create vite@latest . -- --template react-ts
npm install
npm install -D tailwindcss postcss autoprefixer
npx tailwindcss init -p
```

---

## 🔌 API Integration

The frontend connects directly to the OpenBounty Spring Boot backend API:
- **Default Local API Base**: `http://localhost:8080/api/v1`
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI 3 Docs**: `http://localhost:8080/v3/api-docs`

A starter API client is already provided at [src/services/api.js](src/services/api.js) supporting:
- Authentication (`/api/v1/auth/*`)
- Bounties & Challenges (`/api/v1/bounties/*`)
- Developer Proposals (`/api/v1/proposals/*`)
- Milestones & Proofs (`/api/v1/milestones/*`)

---

## 📁 Suggested Directory Architecture

```text
frontend/
├── public/                 # Static assets, icons, logos
├── src/
│   ├── components/         # Reusable UI components (Buttons, Modals, Cards)
│   │   ├── layout/         # Navbar, Footer, Sidebar
│   │   ├── bounty/         # BountyCard, BountyList, BountyFilter
│   │   └── proposal/       # ProposalForm, ProposalItem
│   ├── pages/              # Route views (or app/ for Next.js)
│   │   ├── Home
│   │   ├── Bounties
│   │   ├── BountyDetail
│   │   ├── Dashboard
│   │   └── Profile
│   ├── services/           # HTTP API client wrappers (api.js)
│   ├── styles/             # Global CSS / Tailwind styles
│   ├── App.jsx             # Entry component
│   └── index.js
├── .env.example            # Environment template
├── .gitignore              # Node & build ignores
├── package.json            # Scripts & dependencies
└── README.md
```

---

## 🌿 Git & Pull Request (PR) Workflow

1. **Develop on `frontend` branch**:
   Commit your UI changes exclusively to this directory on the `frontend` branch.
2. **Push to Remote**:
   ```bash
   git push -u origin frontend
   ```
3. **Raise a Pull Request**:
   Create a PR from `frontend` ➔ `main` on GitHub for review, automated CI checks, and staging deployment.
