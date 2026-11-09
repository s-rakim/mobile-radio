FROM node:20-slim

RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    curl \
    && rm -rf /var/lib/apt/lists/*

# JS runtime yt-dlp uses to solve YouTube's player challenges
RUN npm install -g deno

RUN pip3 install --break-system-packages -U yt-dlp yt-dlp-ejs bgutil-ytdlp-pot-provider

WORKDIR /app

COPY package*.json ./
RUN npm install --omit=dev

COPY . .

EXPOSE 3000
CMD ["node", "index.js"]
