import express from 'express';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { Zalo } from 'zalo-api-final';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const app = express();
app.use(express.json());
app.use(express.static(__dirname));

const zalo = new Zalo({
    selfListen: false,
    checkUpdate: false,
    logging: true
});

let api = null;
let eventQueue = [];

async function startZalo() {
    try {
        if (fs.existsSync('cookie.json')) {
            console.log("✅ Tìm thấy phiên đăng nhập cũ...");
            const savedData = JSON.parse(fs.readFileSync('cookie.json', 'utf8'));
            api = await zalo.login({
                cookie: savedData.cookie,
                imei: savedData.imei,
                userAgent: savedData.userAgent
            });

        } else {
            console.log("⚠️ Đang tạo mã QR...");

            // Hẹn 2 giây cho file qr.png kịp tạo ra
            setTimeout(() => {
                eventQueue.push({
                    action: 'REQUIRE_QR',
                    data: { url: `http://127.0.0.1:5000/qr.png?t=${Date.now()}` }
                });
            }, 2000);

            // loginQR({ qrPath }) là đúng theo zalo.ts dòng 244-284
            api = await zalo.loginQR({ qrPath: './qr.png' });

            // ✅ Lưu cookie đúng: dùng api.getCookie() trả về Cookie[]
            //    KHÔNG dùng api.context.cookie (đó là CookieJar object, không serialize được)
            const sessionData = {
                cookie: api.getCookie(),
                imei: api.getContext().imei,
                userAgent: api.getContext().options.userAgent
            };
            fs.writeFileSync('cookie.json', JSON.stringify(sessionData));
        }

        // Báo Python đăng nhập thành công
        eventQueue.push({
            action: 'LOGIN_SUCCESS',
            data: { name: 'ZAuto (Đã kết nối)', avatar: 'profile.jpg' }
        });

        // Lấy danh sách nhóm
        // getAllGroups trả về { version, gridVerMap: { groupId: version } }
        const groups = await api.getAllGroups();
        if (groups && groups.gridVerMap) {
            const groupIds = Object.keys(groups.gridVerMap);
            eventQueue.push({ action: 'GROUPS_DATA', data: { groups: groupIds } });
        }

        // Lắng nghe tin nhắn
        api.listener.on('message', (msg) => {
            // ✅ Lọc tin nhắn tự gửi (uidFrom == "0" nghĩa là của mình)
            if (msg.isSelf) return;

            // ✅ Chỉ xử lý tin nhắn nhóm (type = ThreadType.Group = 1)
            if (msg.type !== 1) return;

            // ✅ Chỉ xử lý text (content có thể là string | object nếu là ảnh/file)
            const content = msg.data.content;
            if (typeof content !== 'string') return;

            eventQueue.push({
                action: 'WEB_NEW_MSG',
                data: {
                    group_name: msg.threadId,   // threadId = groupId (data.idTo)
                    group_id: msg.threadId,
                    text: content,
                    msg_id: msg.data.msgId
                }
            });
        });

        api.listener.start();
        console.log("🎉 Zalo đã kết nối và đang lắng nghe tin nhắn...");

    } catch (error) {
        console.error("❌ Lỗi Zalo:", error);
        eventQueue.push({ action: 'LOGIN_ERROR', data: { error: String(error) } });
    }
}

app.get('/api/events', (req, res) => {
    res.json({ events: eventQueue });
    eventQueue = [];
});

app.post('/api/reply', async (req, res) => {
    if (!api) return res.status(400).json({ error: "Chưa kết nối" });
    try {
        // ✅ sendMessage đúng cú pháp: ({ msg }, threadId, threadType)
        // threadType = 1 = ThreadType.Group
        await api.sendMessage(
            { msg: req.body.message },
            req.body.group_id,
            1
        );
        res.json({ status: "success" });
    } catch (error) {
        res.status(500).json({ error: String(error) });
    }
});

app.listen(5000, () => {
    console.log('⚡ Server chạy ở cổng 5000...');
    startZalo();
});
