/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,jsx,ts,tsx}"],
  theme: {
    extend: {
      colors: {
        // Pathyana brand palette — warm, earthy, Indian heritage
        terracotta: {
          50:  '#fdf5f1',
          100: '#fae4d8',
          200: '#f4c4ad',
          300: '#eba17f',
          400: '#e07d56',
          500: '#d35e3a',  // primary
          600: '#bd4929',
          700: '#9d3a23',
          800: '#7e3220',
          900: '#682c1e',
        },
        saffron: {
          50:  '#fff8eb',
          100: '#ffeac7',
          200: '#ffd084',
          300: '#ffb547',
          400: '#ff9c1a',  // accent
          500: '#f47e08',
          600: '#d85e02',
          700: '#b04305',
          800: '#8e360c',
          900: '#752e0d',
        },
        forest: {
          50:  '#f3f7f2',
          100: '#e0ebde',
          200: '#c2d7be',
          300: '#9bba95',
          400: '#739a6c',
          500: '#557d4f',  // deep green for trust/medical
          600: '#3f6239',
          700: '#324d2f',
          800: '#293e27',
          900: '#223421',
        },
        cream: {
          50:  '#fdfcf8',
          100: '#faf6ec',
          200: '#f3ead0',
          300: '#ebdab0',
          400: '#dec386',
          500: '#cda955',
          DEFAULT: '#faf6ec',
        },
        ink: {
          DEFAULT: '#2a221c', // warm dark for text, not pure black
          soft: '#5c5048',
          muted: '#8b7d72',
        },
      },
      fontFamily: {
        // Serif for headings, sans for body — gives clinical+warm feel
        serif: ['Lora', 'Georgia', 'serif'],
        sans:  ['Inter', '-apple-system', 'BlinkMacSystemFont', 'sans-serif'],
      },
      boxShadow: {
        // Softer shadows for cards
        soft: '0 2px 8px rgba(42, 34, 28, 0.06)',
        medium: '0 4px 16px rgba(42, 34, 28, 0.08)',
      },
    },
  },
  plugins: [],
}
