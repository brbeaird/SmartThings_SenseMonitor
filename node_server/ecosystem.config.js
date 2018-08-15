module.exports = {    
    apps : [      
      {
        name      : 'smartthings_sense_monitor',
        script    : 'server.js',
        watch: true,
      trace: false,
      min_uptime: "5s",
      max_restarts: 10,
      restart_delay: 5000,
        env: {
          PORT: 8000          
        },
        env_production : {
          NODE_ENV: 'production',
          PORT: 8000      
        }
      }      
    ]    
  };
  