FROM node:11
WORKDIR /app
COPY package.json /app
COPY server.js /app
COPY config.js /app
COPY unofficial-sense.js /app
COPY ecosystem.config.js /app
RUN npm install
COPY . /app
CMD node server.js
EXPOSE 9021
