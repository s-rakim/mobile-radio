const { execFile } = require('child_process');
const path = require('path');

const SCRIPT_PATH = path.join(__dirname, '..', 'youtube_fetch.py');

module.exports = async (query) => {
  return new Promise((resolve) => {
    execFile('python3', ['-u', SCRIPT_PATH, query], { timeout: 60000 }, (error, stdout, stderr) => {
      if (error) {
        // Capture the Python traceback if it crashes
        const combinedError = `Error: ${error.message} | Stderr: ${stderr} | Stdout: ${stdout}`;
        console.warn('[youtube-docker] Python error:', combinedError);
        return resolve([{ error: combinedError }]); 
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
        resolve([{ error: "Parse failed: " + stdout + " | Stderr: " + stderr }]);
      }
    });
  });
};