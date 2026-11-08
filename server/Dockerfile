FROM node:20-slim

# Install system dependencies
RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    curl \
    unzip \
    && rm -rf /var/lib/apt/lists/*

# Install Deno via npm (bypasses the slow curl script)
RUN npm install -g deno

# Install yt-dlp and the EJS plugin
RUN pip3 install --break-system-packages -U yt-dlp yt-dlp-ejs

WORKDIR /app

# Install Node dependencies
COPY package*.json ./
RUN npm install --omit=dev

# Copy the rest of the app
COPY . .

EXPOSE 3000
CMD ["node", "index.js"]