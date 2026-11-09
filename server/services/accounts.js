// Accounts and saved libraries. Everything lives in plain JSON files under DATA_DIR
// (a Docker volume), so there's no database to run. Passwords are stored as scrypt
// hashes, and login tokens are only kept hashed too.

const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, '..', 'data');
const USERS_FILE = path.join(DATA_DIR, 'users.json');
const LIBRARY_DIR = path.join(DATA_DIR, 'libraries');
const ALLOW_SIGNUPS = process.env.ALLOW_SIGNUPS !== 'false';

fs.mkdirSync(LIBRARY_DIR, { recursive: true });

function readJson(file, fallback) {
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch {
    return fallback;
  }
}

// Write to a temp file and rename, so a crash mid-write never leaves half a file.
function writeJson(file, data) {
  const tmp = `${file}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify(data));
  fs.renameSync(tmp, file);
}

let users = readJson(USERS_FILE, {}); // username -> { salt, hash, tokens: [sha256], createdAt }
const saveUsers = () => writeJson(USERS_FILE, users);

const sha256 = (text) => crypto.createHash('sha256').update(text).digest('hex');
const hashPassword = (password, salt) => crypto.scryptSync(password, salt, 64).toString('hex');

function checkPassword(user, password) {
  const given = Buffer.from(hashPassword(password, user.salt), 'hex');
  const stored = Buffer.from(user.hash, 'hex');
  return given.length === stored.length && crypto.timingSafeEqual(given, stored);
}

function newToken(user) {
  const token = crypto.randomBytes(32).toString('hex');
  // keep the 10 most recent devices signed in
  user.tokens = [sha256(token), ...(user.tokens || [])].slice(0, 10);
  saveUsers();
  return token;
}

class AuthError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

function cleanUsername(raw) {
  const name = String(raw || '').trim().toLowerCase();
  if (!/^[a-z0-9_.-]{3,32}$/.test(name)) {
    throw new AuthError(400, 'Usernames are 3 to 32 letters, numbers, dots, dashes or underscores');
  }
  return name;
}

function register(rawName, password) {
  if (!ALLOW_SIGNUPS) throw new AuthError(403, 'New accounts are turned off on this server');
  const name = cleanUsername(rawName);
  if (String(password || '').length < 6) throw new AuthError(400, 'Use a password of at least 6 characters');
  if (users[name]) throw new AuthError(409, 'That username is taken');

  const salt = crypto.randomBytes(16).toString('hex');
  users[name] = { salt, hash: hashPassword(password, salt), tokens: [], createdAt: Date.now() };
  return { username: name, token: newToken(users[name]) };
}

function login(rawName, password) {
  const name = cleanUsername(rawName);
  const user = users[name];
  if (!user || !checkPassword(user, String(password || ''))) {
    throw new AuthError(401, 'Wrong username or password');
  }
  return { username: name, token: newToken(user) };
}

function userForToken(token) {
  if (!token) return null;
  const hashed = sha256(token);
  return Object.keys(users).find((name) => (users[name].tokens || []).includes(hashed)) || null;
}

function logout(token) {
  const name = userForToken(token);
  if (!name) return;
  const hashed = sha256(token);
  users[name].tokens = users[name].tokens.filter((t) => t !== hashed);
  saveUsers();
}

const libraryFile = (name) => path.join(LIBRARY_DIR, `${name}.json`);

function getLibrary(name) {
  return readJson(libraryFile(name), { library: null, updatedAt: 0 });
}

function putLibrary(name, library, updatedAt) {
  const record = { library, updatedAt: Number(updatedAt) || Date.now() };
  writeJson(libraryFile(name), record);
  return record.updatedAt;
}

// A handful of wrong passwords per minute per address, so a public URL can't be brute-forced.
const attempts = new Map();
function rateLimited(ip) {
  const now = Date.now();
  const recent = (attempts.get(ip) || []).filter((t) => now - t < 60_000);
  recent.push(now);
  attempts.set(ip, recent);
  return recent.length > 10;
}

module.exports = { AuthError, register, login, logout, userForToken, getLibrary, putLibrary, rateLimited };
