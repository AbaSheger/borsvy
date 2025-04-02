/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone',
  reactStrictMode: true,
  swcMinify: true,
  experimental: {
    outputFileTracingRoot: undefined,
  },
  // Ensure proper static file handling
  poweredByHeader: false,
  compress: true,
  generateEtags: true,
}

module.exports = nextConfig 