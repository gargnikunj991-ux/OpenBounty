import React from 'react';

export default function App() {
  return (
    <div style={{ fontFamily: 'system-ui, sans-serif', padding: '2rem', maxWidth: '800px', margin: '0 auto' }}>
      <h1>🚀 OpenBounty Web Portal</h1>
      <p>This is the placeholder client for the OpenBounty marketplace.</p>
      <div style={{ background: '#f4f4f5', padding: '1.5rem', borderRadius: '8px', marginTop: '1rem' }}>
        <h3>Ready to build your UI:</h3>
        <ul>
          <li>Initialize with Next.js or Vite + React (see <code>README.md</code>)</li>
          <li>Connects to Spring Boot backend at <code>http://localhost:8080/api/v1</code></li>
          <li>Branch: <code>frontend</code> (raise PRs against <code>main</code>)</li>
        </ul>
      </div>
    </div>
  );
}
