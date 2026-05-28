import express from 'express';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { Zalo, LoginQRCallbackEventType } from 'zalo-api-final';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const app = express();
app.use(express.json());
app.use(express.static(__dirname)); // Cho Python tải qr.png

// ─────────────────────────────────────────────
//  KHỞI TẠO
// ─────────────────────────────────────────────
const zalo = new Zalo({
    selfListen: false,   // KHÔNG nhận lại tin mình gửi
    checkUpdate: false,
    logging: true
});

let api = null;
let eventQueue = [];

// ─────────────────────────────────────────────
//  HÀM LƯU SESSION
//  Theo loginQR.ts dòng 512-514:
//  cookies = ctx.cookie.toJSON().cookies  →  SerializedCookie[]
//  Theo getCookie.ts: getCookie() trả về ctx.cookie (CookieJar)
//  => Phải dùng api.getContext().cookie.toJSON().cookies để lưu
// ─────────────────────────────────────────────
function saveSession() {
    try {
        const ctx = api.getContext();
        const sessionData = {
            cookie: ctx.cookie.toJSON().cookies,
            imei: ctx.imei,
            userAgent: ctx.userAgent  // ✅ sửa đây
        };
        fs.writeFileSync('cookie.json', JSON.stringify(sessionData, null, 2));
        console.log('✅ Đã lưu session vào cookie.json');
    } catch (e) {
        console.error('❌ Lỗi lưu session:', e);
    }
}

// ─────────────────────────────────────────────
//  HÀM KHỞI ĐỘNG LẮNG NGHE
// ─────────────────────────────────────────────
function startListener() {
    // Theo listen.ts dòng 34-51, các event hỗ trợ:
    // 'message', 'group_event', 'friend_event', 'reaction',
    // 'typing', 'seen_messages', 'delivered_messages', 'undo'
    // 'connected', 'disconnected', 'error'

    api.listener.on('connected', () => {
        console.log('🔌 WebSocket đã kết nối!');
    });

    api.listener.on('disconnected', (reason) => {
        console.warn('⚠️ WebSocket ngắt kết nối, lý do:', reason);
    });

    api.listener.on('error', (err) => {
        console.error('❌ Lỗi listener:', err);
    });

    // ─── TIN NHẮN TEXT VÀ TIN THOẠI ───
    // Theo Message.ts:
    //   msg.type = ThreadType.User(0) hoặc ThreadType.Group(1)
    //   msg.threadId = groupId (với nhóm) hoặc userId (với cá nhân)
    //   msg.isSelf = true nếu mình gửi (uidFrom == "0")
    //   msg.data.content = string (text) | object (ảnh/file/thoại)
    //   msg.data.msgId = ID tin nhắn
    //   msg.data.uidFrom = ID người gửi
    //   msg.data.dName = Tên người gửi
    //   msg.data.msgType = loại tin: "chat.voice"(thoại), "chat.photo", "webchat"(text)...
    api.listener.on('message', (msg) => {
        // Bỏ qua tin mình tự gửi
        if (msg.isSelf) return;

        const content = msg.data.content;
        const msgType = msg.data.msgType;   // "webchat" = text, "chat.voice" = thoại
        const isGroup = msg.type === 1;     // ThreadType.Group = 1

        // ── TIN NHẮN TEXT ──
        if (typeof content === 'string' && content.trim() !== '') {
            eventQueue.push({
                action: 'WEB_NEW_MSG',
                data: {
                    msg_type: 'text',
                    group_id: msg.threadId,
                    group_name: msg.threadId,
                    sender_id: msg.data.uidFrom,
                    sender_name: msg.data.dName || '',
                    text: content,
                    msg_id: msg.data.msgId,
                    is_group: isGroup,
                    raw_msg_type: msgType
                }
            });
        }

        // ── TIN NHẮN THOẠI ──
        // msgType = "chat.voice", content là object { href: url_file_voice, ... }
        if (msgType === 'chat.voice' && typeof content === 'object' && content !== null) {
            const voiceUrl = content.href || content.fileUrl || '';
            if (voiceUrl) {
                eventQueue.push({
                    action: 'WEB_NEW_VOICE',
                    data: {
                        msg_type: 'voice',
                        group_id: msg.threadId,
                        group_name: msg.threadId,
                        sender_id: msg.data.uidFrom,
                        sender_name: msg.data.dName || '',
                        voice_url: voiceUrl,
                        msg_id: msg.data.msgId,
                        is_group: isGroup,
                        // Gửi toàn bộ data gốc để Python có thể dùng quote khi reply
                        raw_data: {
                            content: msg.data.content,
                            msgType: msg.data.msgType,
                            msgId: msg.data.msgId,
                            cliMsgId: msg.data.cliMsgId,
                            ts: msg.data.ts,
                            ttl: msg.data.ttl,
                            uidFrom: msg.data.uidFrom,
                            propertyExt: msg.data.propertyExt
                        }
                    }
                });
            }
        }

        // ── TIN ẢNH / FILE (nếu cần sau này) ──
        // msgType = "chat.photo", "chat.file"...
    });

    // listener.start({ retryOnClose: true }) = tự kết nối lại khi bị ngắt
    api.listener.start({ retryOnClose: true });
    console.log('👂 Đang lắng nghe tin nhắn Zalo...');
}

// ─────────────────────────────────────────────
//  HÀM ĐĂNG NHẬP ZALO
// ─────────────────────────────────────────────
async function startZalo() {
    try {
        // ── ĐĂNG NHẬP BẰNG COOKIE CŨ ──
        if (fs.existsSync('cookie.json')) {
            console.log('✅ Tìm thấy phiên đăng nhập cũ, đang đăng nhập...');
            const savedData = JSON.parse(fs.readFileSync('cookie.json', 'utf8'));

            // Theo zalo.ts dòng 203-208 và Credentials type dòng 157-162:
            // login() nhận: { cookie: Cookie[] | SerializedCookie[], imei, userAgent }
            api = await zalo.login({
                cookie: savedData.cookie,     // SerializedCookie[] đã lưu đúng
                imei: savedData.imei,
                userAgent: savedData.userAgent
            });

            console.log('✅ Đăng nhập cookie thành công!');
			saveSession(); // ✅ refresh cookie mới nhất
        }

        // ── ĐĂNG NHẬP BẰNG QR ──
        else {
            console.log('⚠️ Chưa có phiên đăng nhập, đang tạo mã QR...');

            // Theo loginQR.ts dòng 370-520:
            // loginQR(options, callback) với callback nhận các event:
            //   QRCodeGenerated → có actions.saveToFile() để lưu ảnh
            //   QRCodeExpired   → QR hết hạn
            //   QRCodeScanned   → người dùng đã quét
            //   GotLoginInfo    → có cookie, imei, userAgent
            api = await zalo.loginQR(
                { qrPath: './qr.png' },
                (event) => {
                    if (event.type === LoginQRCallbackEventType.QRCodeGenerated) {
                        // Lưu ảnh QR ra file rồi báo Python tải về
                        event.actions.saveToFile('./qr.png').then(() => {
                            setTimeout(() => {
                                eventQueue.push({
                                    action: 'REQUIRE_QR',
                                    data: { url: `http://127.0.0.1:5000/qr.png?t=${Date.now()}` }
                                });
                            }, 500);
                        });
                    }

                    if (event.type === LoginQRCallbackEventType.QRCodeExpired) {
                        console.log('⏰ QR hết hạn, đang tạo lại...');
                        eventQueue.push({ action: 'QR_EXPIRED', data: {} });
                        // event.actions.retry() tự động tạo QR mới
                        event.actions.retry();
                    }

                    if (event.type === LoginQRCallbackEventType.QRCodeScanned) {
                        console.log('📱 Đã quét QR:', event.data.display_name);
                        eventQueue.push({
                            action: 'QR_SCANNED',
                            data: { name: event.data.display_name, avatar: event.data.avatar }
                        });
                    }

                    if (event.type === LoginQRCallbackEventType.QRCodeDeclined) {
                        console.log('❌ Người dùng từ chối đăng nhập');
                        eventQueue.push({ action: 'QR_DECLINED', data: {} });
                    }

                    // GotLoginInfo: có cookie, imei, userAgent để lưu
                    // Nhưng lúc này api chưa có, lưu sau khi loginQR resolve
                }
            );

            // loginQR đã resolve → api đã sẵn sàng → lưu session
            saveSession();
            console.log('✅ Đăng nhập QR thành công!');
        }

        // ── BÁO PYTHON ĐĂNG NHẬP THÀNH CÔNG ──
        eventQueue.push({
            action: 'LOGIN_SUCCESS',
            data: { name: 'ZAuto (Đã kết nối)', avatar: 'profile.jpg' }
        });

        // ── LẤY DANH SÁCH NHÓM ──
        // Theo getAllGroups.ts: trả về { version, gridVerMap: { [groupId]: version } }
        const groups = await api.getAllGroups();
        if (groups && groups.gridVerMap) {
            const groupIds = Object.keys(groups.gridVerMap);
            console.log(`📋 Tìm thấy ${groupIds.length} nhóm`);
            eventQueue.push({
                action: 'GROUPS_DATA',
                data: { groups: groupIds }
            });
        }

        // ── BẮT ĐẦU LẮNG NGHE ──
        startListener();

    } catch (error) {
        console.error('❌ Lỗi khởi động Zalo:', error);
        eventQueue.push({
            action: 'LOGIN_ERROR',
            data: { error: String(error) }
        });
    }
}

// ─────────────────────────────────────────────
//  API ENDPOINTS CHO PYTHON
// ─────────────────────────────────────────────

// Python gọi mỗi 1-2 giây để nhận sự kiện mới
app.get('/api/events', (req, res) => {
    res.json({ events: eventQueue });
    eventQueue = []; // Xoá queue sau khi đã gửi
});

// Python gửi tin nhắn chốt cuốc / trả lời
// body: { message, group_id, quote_msg_id (optional), quote_raw_data (optional) }
app.post('/api/reply', async (req, res) => {
    if (!api) return res.status(400).json({ error: 'Chưa kết nối Zalo' });

    try {
        const { message, group_id, quote_raw_data } = req.body;

        // Theo sendMessage.ts dòng 612-619:
        // sendMessage(message: MessageContent | string, threadId, type: ThreadType)
        // MessageContent = { msg, quote?, mentions?, attachments?, styles?, urgency?, ttl? }
        // ThreadType.Group = 1, ThreadType.User = 0

        let messageContent;

        if (quote_raw_data) {
            // Gửi reply đè lên đúng tin nhắn gốc (quote)
            // SendMessageQuote cần: content, msgType, propertyExt, uidFrom, msgId, cliMsgId, ts, ttl
            messageContent = {
                msg: message,
                quote: {
					content: typeof quote_raw_data.content === 'object' 
						? JSON.stringify(quote_raw_data.content)  // ✅ voice/photo content
						: quote_raw_data.content,
					msgType: quote_raw_data.msgType,
					propertyExt: quote_raw_data.propertyExt,
					uidFrom: quote_raw_data.uidFrom,
					msgId: quote_raw_data.msgId,
					cliMsgId: quote_raw_data.cliMsgId,
					ts: quote_raw_data.ts,
					ttl: quote_raw_data.ttl || 0
				}
            };
        } else {
            // Gửi tin nhắn thường không quote
            messageContent = { msg: message };
        }

        // ThreadType.Group = 1
        // body thêm field is_group từ Python gửi lên
		const { message, group_id, quote_raw_data, is_group } = req.body;
		const threadType = is_group ? 1 : 0;  // ThreadType.Group=1, User=0
		await api.sendMessage(messageContent, group_id, threadType);
        res.json({ status: 'success' });

    } catch (error) {
        console.error('❌ Lỗi gửi tin:', error);
        res.status(500).json({ error: String(error) });
    }
});

// Python yêu cầu tạo lại QR (khi người dùng ấn đăng nhập lại)
app.post('/api/restart', async (req, res) => {
    try {
        // Xoá cookie cũ nếu có
        if (fs.existsSync('cookie.json')) fs.unlinkSync('cookie.json');
        if (api) {
            try { api.listener.stop(); } catch (_) {}
            api = null;
        }
        res.json({ status: 'restarting' });
        // Khởi động lại sau 500ms
        setTimeout(startZalo, 500);
    } catch (e) {
        res.status(500).json({ error: String(e) });
    }
});

// ─────────────────────────────────────────────
//  KHỞI ĐỘNG SERVER
// ─────────────────────────────────────────────
app.listen(5000, '127.0.0.1', () => {
    console.log('⚡ Server chạy ở cổng 5000...');
    startZalo();
});
