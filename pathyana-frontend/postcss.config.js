/**
 * PostCSS config — required for Tailwind v3 to work with Vite.
 *
 * Why this file exists in our chunk:
 *   `npx tailwindcss init -p` should have created this, but sometimes it writes
 *   CommonJS syntax (module.exports = ...) into a project where package.json says
 *   "type": "module", causing a confusing "module is not defined in ES module scope" error.
 *
 *   We use export default to match the ES module convention Vite uses.
 *
 * If you already have a working postcss.config.js, leave it alone.
 */
export default {
  plugins: {
    tailwindcss: {},
    autoprefixer: {},
  },
}
