FROM node:20-slim

# Install system dependencies, Python, Tailscale, and wget
RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    curl \
    unzip \
    wget \
    && curl -fsSL https://tailscale.com/install.sh | sh \
    && rm -rf /var/lib/apt/lists/*

# Install Deno via npm
RUN npm install -g deno

# Install yt-dlp, EJS plugin, AND the BGutil POT provider plugin
RUN pip3 install --break-system-packages -U yt-dlp yt-dlp-ejs bgutil-ytdlp-pot-provider

WORKDIR /app

COPY package*.json ./
RUN npm install --omit=dev

COPY . .

# Make our startup script executable
RUN chmod +x start.sh

EXPOSE 3000
CMD ["./start.sh"]