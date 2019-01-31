# SmartThings_SenseMonitor
Connects SmartThings with Sense

### Beta Notice and known limitations
* This SmartApp is considered an early "beta" - functionality is limited and there are likely to be bugs. Feel free to <a href="https://github.com/brbeaird/SmartThings_SenseMonitor/issues">create and track issues here</a>.

* The app does not currently handle when Sense devices are deleted - you'll need to manually delete the counterpart in SmartThings
* The app does not offer the ability to exclude certain Sense devices from SmartThings; for now, they will all come over.

### Overview
* This SmartApp is currently only supported in the **SmartThings Classic mobile app**; the new app does not yet support custom apps like this.
* The app requires a node server running on a machine (PC, Raspberry PI, etc.) on the same LAN as your SmartThings hub (don't worry - setup is quite simple). The node server creates a realtime websocket connection with Sense. On relevant events, the node server sends data over your LAN to the hub. The SmartApp listens for this data and then updates devices in SmartThings as needed.
* Special thanks to <a href="https://github.com/blandman">blandman</a> for his work on the unofficial sense API node library

### Device Tile
![Device Tile](https://i.imgur.com/4G3Eo8n.png "Device Tile")


### Device Tile Options
![Device Tile Options](https://i.imgur.com/67yuCcd.png "Device Tile Options")


### SmartApp Options
![SmartApp Options](https://i.imgur.com/IVLCOQ0.png "SmartApp Options")

### With ActionTiles
![With ActionTiles](https://i.imgur.com/I1sY3IL.png "With ActionTiles")

### Device Creation and Sync
* This app automatically creates devices in SmartThings based on Sense devices. It also handles when those devices are renamed.
* Device status (on/off) and power usage is kept in sync within SmartThings. You can use these values to drive various other routines or ActionTiles panels.

### Push Notifications
* This app offers the option to send push notifications when devices turn on or off. Access push notification settings at each individual device in the SmartThings mobile app.
* You can set "quiet modes" during which notifications will not be sent. Go to the Sense SmartApp in the SmartThings mobile app to configure this.



## Installation

There are 2 code files needed: 1 SmartApp and 1 Device Handler.


### Manually:
1. Log in to the <a href="https://graph.api.smartthings.com/ide/">SmartThings IDE</a>. If you don't have a login yet, create one.
2. The first step is to create the device handler.
3. Click on **My Device Handlers** -> **Create new Device Handler** -> **From Code**.
4. Copy contents of <a href="https://raw.githubusercontent.com/brbeaird/SmartThings_SenseMonitor/master/devicetypes/brbeaird/sense-monitor-device.src/sense-monitor-device.groovy">Sense Device </a> and paste into text area. Click **Create**. Click **Publish** > **For Me**
5. Now we create the SmartApp code. Click **My SmartApps** -> **New Smartapp** -> **From Code**.
6. Copy contents of <a href="https://raw.githubusercontent.com/brbeaird/SmartThings_SenseMonitor/master/smartapps/brbeaird/sensemonitor.src/sensemonitor.groovy">SmartApp</a> and paste into text area. Click **Create**. Click **Publish** > **For Me**
7. In your SmartThings mobile app, tap **Automation** -> **SmartApps** -> **Add a SmartApp**. Scroll down and tap **My Apps**. Tap **Sense**. Tap save to complete the installation. Your SmartApp is now listening for Sense data. Move on to the node server setup! 

### SmartThings IDE GitHub Integration:

If you have not set up the GitHub integration yet or do not know about it, take a look at the SmartThings documentation [here](http://docs.smartthings.com/en/latest/tools-and-ide/github-integration.html). Note that if you do not have a GitHub account or are not familiar with GitHub, the manual method of installation is recommended.

1. If you haven't already, click on enable GitHub button (upper right). Add a new repository with user `brbeaird`, repository `SmartThings_SenseMonitor`, and branch `master`. This can be done in either the "My Device Handlers" or "My SmartApps" sections
2. Go to "My Device Handlers". Click "Update from Repo". Select the "SmartThings_SenseMonitor" repository. You should see the device type in the "New (only in GitHub)" section. Check the box next to it. Check the "Publish" checkbox in the bottom right hand corner. Click "Execute Update".
3. Go to "My SmartApps". Click "Update from Repo". Select the "SmartThings_SenseMonitor" repository. You should see the SmartApp in the "New (only in GitHub)" section. Check both box next to it. Check the "Publish" checkbox in the bottom right hand corner. Click "Execute Update".
4. In your SmartThings mobile app, tap **Automation** -> **SmartApps** -> **Add a SmartApp**. Scroll down and tap **My Apps**. Tap **Sense**. Tap save to complete the installation. Your SmartApp is now listening for Sense data. Move on to the node server setup! 

In the future, should you wish to update, simply repeat steps 2 and 3. The only difference is you will see the device type/SmartApp show up in the "Obsolete (updated in GitHub)" column instead.

### Node Server Setup
 1. If you don't already have it, <a href="https://nodejs.org/en/download/">Download and install Node.js</a>
 2. Download (or git clone) the  files <a href="https://github.com/brbeaird/SmartThings_SenseMonitor">in this repository</a>. If you're new to git, you can click the green Download button and grab a zip file of everything. Extract the zip file.
 3. Open a command prompt and navigate to the location where you downloaded the files in step 2. Navigate down to SmartThings_SenseMonitor\node_server. 
 4. Run `npm install` (this grabs needed libraries)
 5. Open the server.js file in a text editor and put in your Sense login information and SmartThings hub IP under the required settings section. You can find your hub IP in the SmartThings IDE by clicking the Hub link, then clicking your hub, then scrolling down to IP Address. Be sure to save your changes.
 6. Run `node server.js`. This starts up the data connection. If all goes well, you should see a successful connection message. Leave this window running to continue collecting data and sending it to SmartThings.
 7. I strongly recommend using something like PM2 to keep the node server running in the background. Will add more detailed steps on that later.

### Running as a Docker Container
 1. Install Docker (see: <a href="https://docs.docker.com/get-started/">Docker - Get Started</a>) and Docker Compose for your environment. 
 2. Change to the node_server directory.
 3. Copy or rename the docker.env.dist file to docker.env (`cp docker.env.dist docker.env`)
 4. Edit the docker.env file and update the sense email, sense password, and smartthingsHubIp variables. Notes: 1) Quotes may cause passed parameters to be invalid. 2) Port must match the port exposed in the docker-compose.yml file (port 9021). The port can be changed, but needs to be updated in all places referenced.
 5. Save the docker.env file.
 6. Run `docker-commpose up` Add the -d option to run the container detached in the background: `docker-compose up -d`. The first time `docker-compose up` is ran, Docker will build the image file used to run the Sense Monitor.
 
 Note: Upon start up, docker-compose will make a copy of config-docker.js to use instead of config.js, the config-docker.js uses parameters from the docker.env file. 

### List docker containers running
 1. Run `docker-compose ps` or `docker ps`

### To stop a Docker process, get container id from above command:
 1. Run `docker-compose stop` or can run `docker stop <containerId>` (from `docker ps`) if you have more than one container running.

### Rebuild the Docker image
 1. Update files for this project from github.
 2. Change to the node_server directory.
 3. Either run `docker-compose build` or `docker-compose up --build`
 
### Work with a running Docker container
 1. Run `docker ps` to view the containers id
 2. Run `docker exec -it <containerId> bash` to open a bash window. Keep in mind that commands are limited since the container only has what it needs to run.
 3. Type `exit` when you are ready to close the bash prompt.'
 
### Remove a Docker image
 1. Run `docker images` to get a list of images
 2. Run `docker rmi <imageId>` to remove a specific image. 