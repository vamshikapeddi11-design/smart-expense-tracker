# ==============================================================================
# Smart Expense Tracker — frontend (Vite dev server)
# Used for local development: installs dependencies and serves the app with
# hot module reload on port 5173.
# ==============================================================================

FROM node:20-alpine

WORKDIR /app

# Install dependencies first for better layer caching.
COPY package*.json ./
RUN npm install

# Then copy the rest of the source.
COPY . .

EXPOSE 5173

# Vite dev server, bound to all interfaces so it is reachable from the host.
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0", "--port", "5173"]
