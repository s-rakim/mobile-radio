const { execFile } = require('child_process');
const path = require('path');

const SCRIPT_PATH = path.join(__dirname, '..', 'youtube_fetch.py');

module.exports = async (query) => {
  return new Promise((resolve) => {
    execFile('python3', ['-u', SCRIPT_PATH, query], { timeout: 30000 }, (error, stdout, stderr) => {
      if (error) {
        console.warn('[youtube-docker] Python error:', error.message);
        return resolve([{ error: error.message }]); 
      }
      
      try {
        const data = JSON.parse(stdout.trim());
        if (data.error) {
          console.warn('[youtube-docker] Scraper error:', data.error);
          return resolve([{ error: data.error }]);
        }
        resolve([data]); 
      } catch (e) {
        console.warn('[youtube-docker] Failed to parse JSON:', stdout);
        resolve([{ error: "Parse failed: " + stdout }]);
      }
    });
  });
};