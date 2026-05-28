import express from 'express';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { Zalo } from 'zalo-api-final';

// 2 Dòng này để lấy đường dẫn thư mục hiện tại
const __dirname = path.dirname(fileURLToPath(import.meta.url));

const app = express();
app.use(express.json());

// BẬT TÍNH NĂNG CHIA SẺ FILE (Để Python có thể tải ảnh qr.png)
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
        } 
        else {
            console.log("⚠️ Đang tạo mã QR...");
            
            // Hẹn giờ 2 giây (chờ file qr.png được tạo ra) rồi báo cho Python tải ảnh về
            setTimeout(() => {
                eventQueue.push({ 
                    action: 'REQUIRE_QR', 
                    // Thêm Date.now() để chống điện thoại lưu bộ nhớ đệm (cache) ảnh cũ
                    data: { url: `http://127.0.0.1:5000/qr.png?t=${Date.now()}` } 
                });
            }, 2000);

            // Hàm này sẽ dừng ở đây để chờ người dùng lấy điện thoại quét mã
            api = await zalo.loginQR({ qrPath: './qr.png' });
            
            // Quét xong mới chạy tiếp xuống đây
            const sessionData = {
                cookie: api.context.cookie,
                imei: api.context.imei,
                userAgent: api.context.options.userAgent
            };
            fs.writeFileSync('cookie.json', JSON.stringify(sessionData));
        }

        // Báo cho Python là Đăng nhập thành công, ẩn mã QR đi
        eventQueue.push({ action: 'LOGIN_SUCCESS', data: { name: 'ZAuto (Đã kết nối)', avatar: 'profile.jpg' }});

        const groups = await api.getAllGroups();
        if (groups && groups.gridVerMap) {
            eventQueue.push({ action: 'GROUPS_DATA', data: { groups: Object.keys(groups.gridVerMap) } });
        }

        api.listener.start();
        api.listener.on('message', (msg) => {
            if (msg.type === 1) {
                eventQueue.push({
                    action: 'WEB_NEW_MSG',
                    data: { group_name: msg.threadId, group_id: msg.threadId, text: msg.data.content, msg_id: msg.data.msgId }
                });
            }
        });

    } catch (error) {
        console.error("❌ Lỗi Zalo:", error);
    }
}

app.get('/api/events', (req, res) => {
    res.json({ events: eventQueue });
    eventQueue = [];
});

app.post('/api/reply', async (req, res) => {
    if (!api) return res.status(400).json({ error: "Chưa kết nối" });
    try {
        await api.sendMessage(req.body.message, req.body.group_id, 1);
        res.json({ status: "success" });
    } catch (error) {
        res.status(500).json({ error: String(error) });
    }
});

app.listen(5000, () => {
    console.log('⚡ Server chạy ở cổng 5000...');
    startZalo();
});
