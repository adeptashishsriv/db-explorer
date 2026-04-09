# 📓 Release Notes: DB Explorer v2.5.0 - The "AI Power" Update

**Release Date:** May 22, 2024  
**Publisher:** Astro Adept AI Labs  
**Version:** 2.5.0

---

## 🚀 Overview
This major update transforms DB Explorer from a database management tool with AI features into a truly **AI-First Database Development Environment**. We have completely overhauled our AI integration to support multiple providers, flexible model configurations, and a more responsive, context-aware user experience.

---

## ⭐ New Features

### 1. Multi-Provider AI Support
Connect to your preferred Large Language Model (LLM) with native support for:
*   **OpenAI:** Support for the latest models including `gpt-5-nano` and the `o1` reasoning series.
*   **Anthropic (Claude):** Native integration with Claude 3.5 Sonnet and the upcoming models.
*   **Google Gemini:** High-performance integration with Gemini 1.5 Pro and Flash.
*   **DeepSeek:** Optimized support for DeepSeek’s chat and coder models.

### 2. Unlimited AI Profiles & Persistence
You are no longer limited to a single AI configuration.
*   **Multiple Profiles:** Create and save multiple configurations (e.g., "Work OpenAI", "Research Gemini", "Private Claude").
*   **Profile Management:** Easily add, edit, or delete profiles in the new sidebar-driven configuration window.
*   **Session Persistence:** DB Explorer now remembers your last-used AI profile across sessions, so you can pick up exactly where you left off.

### 3. Strictly Context-Aware Assistant
The AI Assistant is now smarter about your environment:
*   **Database Context Enforced:** The Assistant now requires an active, connected database to function, ensuring suggestions are always grounded in your actual schema.
*   **Visual Context Bar:** A new header in the AI window displays your current database context (e.g., `Database Context: PROD_DB (POSTGRESQL)`).
*   **Smart Prompting:** Automatic schema extraction is now strictly bound to the selected connection to prevent cross-database hallucinations.

---

## 🔧 Enhancements & UI Improvements

*   **Flexible Model Configuration:** The Model Name field is now free-text, allowing you to use newly released models immediately without waiting for a software update.
*   **Smart URL Mapping:** Base URLs now auto-populate based on the selected provider while remaining fully overridable for enterprise proxies.
*   **Visual Feedback:** Added a high-fidelity indeterminate progress bar with the message *"AI is thinking..."* to provide real-time feedback during API calls.
*   **Automatic Naming:** New AI configurations are automatically named using the `<provider>-<model>` format for faster setup.
*   **Dynamic Parameter Handling:**
    *   Automatic switching between `max_tokens` and `max_completion_tokens` for reasoning models.
    *   Conditional `temperature` handling to satisfy stricter API requirements of newer models.

---

## 🏷️ Rebranding
We are proud to announce that our team has transitioned from **Adept Software** to **Astro Adept AI Labs**. This change reflects our commitment to pushing the boundaries of AI-integrated developer tools.

---

## 🛠 Technical Fixes
*   Fixed a 404 error occurring during Claude API test connections.
*   Resolved a bug where the Base URL was not properly clearing when switching between Custom and Native providers.
*   Fixed a UI glitch where the progress bar would occasionally remain hidden after a failed API call.
*   Improved API error reporting to show specific status codes and messages from providers.

---

*Built by Astro Adept AI Labs | © 2024*
