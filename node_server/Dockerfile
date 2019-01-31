
FROM node:11
WORKDIR /app
COPY package.json /app
COPY server.js /app
COPY unofficial-sense.js /app
COPY ecosystem.config.js /app
RUN npm install
COPY . /app
COPY config_docker.js /app/config.js
CMD node server.js