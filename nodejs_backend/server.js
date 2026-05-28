import express from 'express';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { createRequire } from 'module';
import { Zalo, LoginQRCallbackEventType } from 'zalo-api-final';

// ─────────────────────────────────────────────
// ✅ BẢN VÁ COOKIE TỐI THƯỢNG (BỎ QUA MỌI KIỂM TRA TÊN MIỀN)
// ─────────────────────────────────────────────
const require = createRequire(import.meta.url);

function applyCookiePatch(CookieJarClass) {
    if (!CookieJarClass || CookieJarClass.__patched) return;

    // ── PATCH setCookie ──────────────────────────────────────
    const originalSetCookie = CookieJarClass.prototype.setCookie;
    CookieJarClass.prototype.setCookie = function(cookie, url, options, cb) {
        if (typeof options === 'function') { cb = options; options = {}; }
        options = options || {};
        options.ignoreError = true;
        let urlStr = typeof url === 'string' ? url : (url?.href || String(url));
        if (urlStr.includes('id.zalo.me')) url = urlStr.replace('id.zalo.me', 'chat.zalo.me');
        return originalSetCookie.call(this, cookie, url, options, cb);
    };

    const originalSetCookieSync = CookieJarClass.prototype.setCookieSync;
    CookieJarClass.prototype.setCookieSync = function(cookie, url, options) {
        options = options || {};
        options.ignoreError = true;
        let urlStr = typeof url === 'string' ? url : (url?.href || String(url));
        if (urlStr.includes('id.zalo.me')) url = urlStr.replace('id.zalo.me', 'chat.zalo.me');
        return originalSetCookieSync.call(this, cookie, url, options);
    };

    // ── PATCH getCookies (MỚI - QUAN TRỌNG) ─────────────────
    const originalGetCookies = CookieJarClass.prototype.getCookies;
    CookieJarClass.prototype.getCookies = function(url, options, cb) {
        if (typeof options === 'function') { cb = options; options = {}; }
        options = options || {};
        let urlStr = typeof url === 'string' ? url : (url?.href || String(url));
        const altUrl = urlStr.includes('id.zalo.me') ? urlStr.replace('id.zalo.me', 'chat.zalo.me') : urlStr;
        return originalGetCookies.call(this, altUrl, options, cb);
    };

    const originalGetCookiesSync = CookieJarClass.prototype.getCookiesSync;
    CookieJarClass.prototype.getCookiesSync = function(url, options) {
        options = options || {};
        let urlStr = typeof url === 'string' ? url : (url?.href || String(url));
        const altUrl = urlStr.includes('id.zalo.me') ? urlStr.replace('id.zalo.me', 'chat.zalo.me') : urlStr;
        return originalGetCookiesSync.call(this, altUrl, options);
    };

    // ── PATCH getCookieString (MỚI) ──────────────────────────
    const originalGetCookieString = CookieJarClass.prototype.getCookieString;
    CookieJarClass.prototype.getCookieString = function(url, options, cb) {
        if (typeof options === 'function') { cb = options; options = {}; }
        options = options || {};
        let urlStr = typeof url === 'string' ? url : (url?.href || String(url));
        const altUrl = urlStr.includes('id.zalo.me') ? urlStr.replace('id.zalo.me', 'chat.zalo.me') : urlStr;
        return originalGetCookieString.call(this, altUrl, options, cb);
    };

    const originalGetCookieStringSync = CookieJarClass.prototype.getCookieStringSync;
    CookieJarClass.prototype.getCookieStringSync = function(url, options) {
        options = options || {};
        let urlStr = typeof url === 'string' ? url : (url?.href || String(url));
        const altUrl = urlStr.includes('id.zalo.me') ? urlStr.replace('id.zalo.me', 'chat.zalo.me') : urlStr;
        return originalGetCookieStringSync.call(this, altUrl, options);
    };

    CookieJarClass.__patched = true;
    console.log("✅ ZAUTO: Kích hoạt khiên chống lỗi Zalo API (Max Level)!");
}

// Gọi hàm vá lỗi
try { applyCookiePatch(require('zalo-api-final/node_modules/tough-cookie').CookieJar); } catch (e) {}
try { applyCookiePatch(require('tough-cookie').CookieJar); } catch (e) {}

// ─────────────────────────────────────────────
// KHỞI TẠO BIẾN VÀ EXPRESS (ĐOẠN BẠN BỊ XÓA NHẦM)
// ─────────────────────────────────────────────
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const app = express();
app.use(express.json());
app.use(express.static(__dirname)); 

const zalo = new Zalo({ selfListen: false, checkUpdate: false, logging: true });
let api = null;
let eventQueue = [];
let currentUserInfo = { name: 'ZAuto (Đã kết nối)', avatar: 'profile.jpg' };

function saveSession() {
    try {
        const ctx = api.getContext();
        const sessionData = {
            cookie: ctx.cookie.toJSON().cookies, imei: ctx.imei,
            userAgent: ctx.userAgent, userInfo: currentUserInfo
        };
        fs.writeFileSync('cookie.json', JSON.stringify(sessionData, null, 2));
        console.log('✅ Đã lưu session và Profile vào cookie.json');
    } catch (e) {
        console.error('❌ Lỗi lưu session:', e);
    }
}
// ─────────────────────────────────────────────

function startListener() {
    api.listener.on('connected', () => console.log('🔌 WebSocket đã kết nối!'));
    api.listener.on('disconnected', (reason) => console.warn('⚠️ WebSocket ngắt kết nối:', reason));
    api.listener.on('error', (err) => console.error('❌ Lỗi listener:', err));

    api.listener.on('message', (msg) => {
        if (msg.isSelf) return;
        const content = msg.data.content;
        const msgType = msg.data.msgType;   
        const isGroup = msg.type === 1;     

        if (typeof content === 'string' && content.trim() !== '') {
            eventQueue.push({ action: 'WEB_NEW_MSG', data: { msg_type: 'text', group_id: msg.threadId, group_name: msg.threadId, sender_id: msg.data.uidFrom, sender_name: msg.data.dName || '', text: content, msg_id: msg.data.msgId, is_group: isGroup, raw_msg_type: msgType }});
        }
        if (msgType === 'chat.voice' && typeof content === 'object' && content !== null) {
            const voiceUrl = content.href || content.fileUrl || '';
            if (voiceUrl) {
                eventQueue.push({ action: 'WEB_NEW_VOICE', data: { msg_type: 'voice', group_id: msg.threadId, group_name: msg.threadId, sender_id: msg.data.uidFrom, sender_name: msg.data.dName || '', voice_url: voiceUrl, msg_id: msg.data.msgId, is_group: isGroup, raw_data: { content: msg.data.content, msgType: msg.data.msgType, msgId: msg.data.msgId, cliMsgId: msg.data.cliMsgId, ts: msg.data.ts, ttl: msg.data.ttl, uidFrom: msg.data.uidFrom, propertyExt: msg.data.propertyExt } }});
            }
        }
        if (msgType === 'chat.photo' && typeof content === 'object' && content !== null) {
            const photoUrl = content.href || content.normalUrl || content.hdUrl || '';
            if (photoUrl) {
                eventQueue.push({ action: 'WEB_NEW_PHOTO', data: { msg_type: 'photo', group_id: msg.threadId, group_name: msg.threadId, sender_id: msg.data.uidFrom, sender_name: msg.data.dName || '', photo_url: photoUrl, msg_id: msg.data.msgId, is_group: isGroup, raw_data: { content: msg.data.content, msgType: msg.data.msgType, msgId: msg.data.msgId, cliMsgId: msg.data.cliMsgId, ts: msg.data.ts, ttl: msg.data.ttl, uidFrom: msg.data.uidFrom, propertyExt: msg.data.propertyExt } }});
            }
        }
    });

    api.listener.start({ retryOnClose: true });
    console.log('👂 Đang lắng nghe tin nhắn Zalo...');
}

async function startZalo() {
    try {
        if (fs.existsSync('cookie.json')) {
            console.log('✅ Tìm thấy phiên đăng nhập cũ, đang đăng nhập...');
            const savedData = JSON.parse(fs.readFileSync('cookie.json', 'utf8'));
            if (savedData.userInfo) currentUserInfo = savedData.userInfo;
            api = await zalo.login({ cookie: savedData.cookie, imei: savedData.imei, userAgent: savedData.userAgent });
            console.log('✅ Đăng nhập cookie thành công!');
            saveSession(); 
        } else {
            console.log('⚠️ Chưa có phiên đăng nhập, đang tạo mã QR...');
            api = await zalo.loginQR({ qrPath: './qr.png' }, (event) => {
                if (event.type === LoginQRCallbackEventType.QRCodeGenerated) {
                    event.actions.saveToFile('./qr.png').then(() => {
                        setTimeout(() => { eventQueue.push({ action: 'REQUIRE_QR', data: { url: `http://127.0.0.1:5000/qr.png?t=${Date.now()}` } }); }, 500);
                    });
                }
                if (event.type === LoginQRCallbackEventType.QRCodeExpired) {
                    console.log('⏰ QR hết hạn, đang tạo lại...');
                    eventQueue.push({ action: 'QR_EXPIRED', data: {} });
                    event.actions.retry();
                }
                if (event.type === LoginQRCallbackEventType.QRCodeScanned) {
                    console.log('📱 Đã quét QR:', event.data.display_name);
                    currentUserInfo = { name: event.data.display_name, avatar: event.data.avatar };
                    eventQueue.push({ action: 'QR_SCANNED', data: currentUserInfo });
                }
                if (event.type === LoginQRCallbackEventType.QRCodeDeclined) {
                    console.log('❌ Người dùng từ chối đăng nhập');
                    eventQueue.push({ action: 'QR_DECLINED', data: {} });
                }
            });
            saveSession();
            console.log('✅ Đăng nhập QR thành công!');
        }

        eventQueue.push({ action: 'LOGIN_SUCCESS', data: currentUserInfo });

        const groups = await api.getAllGroups();
        if (groups && groups.gridVerMap) {
            const groupIds = Object.keys(groups.gridVerMap);
            console.log(`📋 Tìm thấy ${groupIds.length} nhóm. Đang lấy Avatar...`);
            let groupDetails = [];
            for (const gid of groupIds) {
                try {
                    const info = await api.getGroupInfo(gid);
                    if (info) groupDetails.push({ id: gid, name: info.groupName || info.grpName || info.name || `Nhóm ${gid}`, avatar: info.avatar || info.avt || 'profile.jpg' });
                } catch (e) {
                    groupDetails.push({ name: gid, avatar: 'profile.jpg' });
                }
            }
            eventQueue.push({ action: 'GROUPS_DATA', data: { groups: groupDetails } });
            console.log(`✅ Đã gửi danh sách nhóm lên App!`);
        }
        startListener();

    } catch (error) {
        console.error('❌ Lỗi khởi động Zalo:', error);
        eventQueue.push({ action: 'LOGIN_ERROR', data: { error: String(error) } });
    }
}

app.get('/api/events', (req, res) => { res.json({ events: eventQueue }); eventQueue = []; });

// CỔNG MỚI: NHẬN COOKIE TƯƠI TỪ TRÌNH DUYỆT ĐIỆN THOẠI (WEBVIEW ANDROID)
app.post('/api/cookie_login', async (req, res) => {
    try {
        const { raw_cookie } = req.body;
        if (!raw_cookie) return res.status(400).json({error: 'Thiếu cookie'});
        
        if (fs.existsSync('cookie.json')) fs.unlinkSync('cookie.json');
        if (api) { try { api.listener.stop(); } catch(e){} api = null; }

        console.log('🔄 Đang chuyển đổi Cookie từ WebView...');
        const tempZalo = new Zalo({ selfListen: false, checkUpdate: false, logging: true });
        
        api = await tempZalo.login({ cookie: raw_cookie });

        const ctx = api.getContext();
        const sessionData = {
            cookie: ctx.cookie.toJSON().cookies,
            imei: ctx.imei,
            userAgent: ctx.userAgent,
            userInfo: { name: 'ZAuto (Từ WebView)', avatar: 'profile.jpg' }
        };
        fs.writeFileSync('cookie.json', JSON.stringify(sessionData, null, 2));
        
        startListener();
        res.json({status: 'success'});
    } catch (e) {
        console.error('❌ Lỗi xử lý WebView Cookie:', e);
        res.status(500).json({error: String(e)});
    }
});

app.post('/api/reply', async (req, res) => {
    if (!api) return res.status(400).json({ error: 'Chưa kết nối Zalo' });
    try {
        const { message, group_id, quote_raw_data, is_group } = req.body;
        let messageContent;
        if (quote_raw_data) {
            messageContent = {
                msg: message,
                quote: {
                    content: typeof quote_raw_data.content === 'object' ? JSON.stringify(quote_raw_data.content) : quote_raw_data.content,
                    msgType: quote_raw_data.msgType, propertyExt: quote_raw_data.propertyExt,
                    uidFrom: quote_raw_data.uidFrom, msgId: quote_raw_data.msgId,
                    cliMsgId: quote_raw_data.cliMsgId, ts: quote_raw_data.ts, ttl: quote_raw_data.ttl || 0
                }
            };
        } else {
            messageContent = { msg: message };
        }
        await api.sendMessage(messageContent, String(group_id), is_group ? 1 : 0);
        res.json({ status: 'success' });
    } catch (error) {
        console.error('❌ Lỗi gửi tin:', error);
        res.status(500).json({ error: String(error) });
    }
});

app.post('/api/restart', async (req, res) => {
    try {
        if (fs.existsSync('cookie.json')) fs.unlinkSync('cookie.json');
        if (api) { try { api.listener.stop(); } catch (_) {} api = null; }
        res.json({ status: 'restarting' });
        setTimeout(startZalo, 500);
    } catch (e) { res.status(500).json({ error: String(e) }); }
});

app.listen(5000, '127.0.0.1', () => {
    console.log('⚡ Server chạy ở cổng 5000...');
    startZalo();
});
