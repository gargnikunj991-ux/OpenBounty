/**
 * OpenBounty Frontend API Client
 * Connects to the Spring Boot REST API
 */

const API_BASE_URL = 
  (typeof process !== 'undefined' && process.env?.NEXT_PUBLIC_API_BASE_URL) ||
  (typeof import.meta !== 'undefined' && import.meta.env?.VITE_API_BASE_URL) ||
  'http://localhost:8080/api/v1';

class ApiClient {
  constructor(baseUrl = API_BASE_URL) {
    this.baseUrl = baseUrl;
  }

  getAuthToken() {
    if (typeof window !== 'undefined') {
      return localStorage.getItem('openbounty_token');
    }
    return null;
  }

  async request(endpoint, options = {}) {
    const token = this.getAuthToken();
    const headers = {
      'Content-Type': 'application/json',
      ...options.headers,
    };

    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const response = await fetch(`${this.baseUrl}${endpoint}`, {
      ...options,
      headers,
    });

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}));
      const error = new Error(errorData.message || `Request failed with status ${response.status}`);
      error.status = response.status;
      error.data = errorData;
      throw error;
    }

    if (response.status === 204) {
      return null;
    }

    return response.json();
  }

  // Auth Endpoints
  login(credentials) {
    return this.request('/auth/login', {
      method: 'POST',
      body: JSON.stringify(credentials),
    });
  }

  register(userData) {
    return this.request('/auth/register', {
      method: 'POST',
      body: JSON.stringify(userData),
    });
  }

  // Bounty Endpoints
  getBounties(params = {}) {
    const queryString = new URLSearchParams(params).toString();
    return this.request(`/bounties${queryString ? `?${queryString}` : ''}`);
  }

  getBountyById(id) {
    return this.request(`/bounties/${id}`);
  }

  createBounty(bountyData) {
    return this.request('/bounties', {
      method: 'POST',
      body: JSON.stringify(bountyData),
    });
  }

  // Proposal Endpoints
  getProposalsByBounty(bountyId) {
    return this.request(`/bounties/${bountyId}/proposals`);
  }

  submitProposal(bountyId, proposalData) {
    return this.request(`/bounties/${bountyId}/proposals`, {
      method: 'POST',
      body: JSON.stringify(proposalData),
    });
  }

  // Milestone Endpoints
  getMilestonesByProposal(proposalId) {
    return this.request(`/proposals/${proposalId}/milestones`);
  }
}

export const api = new ApiClient();
export default api;
