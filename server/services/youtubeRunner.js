const { execFile } = require('child_process');
const path = require('path');

// Points to the youtube_fetch.py in the main backend folder
const SCRIPT_PATH = path.join(__dirname, '..', 'youtube_fetch.py');

module.exports = async (query) => {
  return new Promise((resolve) => {
    // Run the python script
    execFile('python3', ['-u', SCRIPT_PATH, query], { timeout: 30000 }, (error, stdout, stderr) => {
      if (error) {
        console.warn('[youtube-docker] Python error:', error.message);
        return resolve([]); 
      }
      
      try {
        const data = JSON.parse(stdout.trim());
        if (data.error) {
          console.warn('[youtube-docker] Scraper error:', data.error);
          return resolve([]);
        }
        resolve([data]); // Return as an array
      } catch (e) {
        console.warn('[youtube-docker] Failed to parse JSON:', stdout);
        resolve([]);
      }
    });
  });
};