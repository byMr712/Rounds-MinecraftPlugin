#!/usr/bin/env python3
"""
Minecraft 26.2 MCP Bridge for Rounds Minigame Testing
Model Context Protocol stdio server with zero dependencies.
"""

import sys
import json
import socket
import struct
import uuid
import zlib
import time
import threading

# ── Minecraft Protocol 776 Helpers ───────────────────────────────────────────

def write_varint(val: int) -> bytes:
    out = bytearray()
    while True:
        b = val & 0x7F
        val >>= 7
        if val != 0:
            b |= 0x80
        out.append(b)
        if val == 0:
            break
    return bytes(out)

def read_varint_from_buf(buf: bytes, offset: int = 0):
    val = 0
    shift = 0
    pos = offset
    while True:
        if pos >= len(buf):
            return None, pos
        byte = buf[pos]
        pos += 1
        val |= (byte & 0x7F) << shift
        if not (byte & 0x80):
            break
        shift += 7
    return val, pos

def read_varint_from_sock(sock: socket.socket):
    val = 0
    shift = 0
    while True:
        b = sock.recv(1)
        if not b:
            return None
        byte = b[0]
        val |= (byte & 0x7F) << shift
        if not (byte & 0x80):
            break
        shift += 7
    return val

# ── Bot Implementation ────────────────────────────────────────────────────────

class MinecraftBot:
    def __init__(self, name: str, is_admin: bool = False):
        self.name = name
        self.is_admin = is_admin
        if is_admin or name == '_mr713_':
            self.uuid = uuid.UUID('0837ed51-0b18-31bd-857f-430260727b38')
        else:
            self.uuid = uuid.uuid5(uuid.NAMESPACE_DNS, f'OfflinePlayer:{name}')
        self.sock = None
        self.compression = -1
        self.running = False
        self.in_play = False
        self.last_container_id = None
        self.container_state_id = 0
        self.entity_id = None
        self.thread = None

    def send_packet(self, pid: int, payload: bytes):
        if not self.sock:
            return
        data = write_varint(pid) + payload
        if self.compression >= 0:
            if len(data) >= self.compression:
                comp = zlib.compress(data)
                packet = write_varint(len(data)) + comp
            else:
                packet = write_varint(0) + data
            self.sock.sendall(write_varint(len(packet)) + packet)
        else:
            self.sock.sendall(write_varint(len(data)) + data)

    def recv_packet(self):
        pkt_len = read_varint_from_sock(self.sock)
        if pkt_len is None:
            return None, None
        raw = b''
        while len(raw) < pkt_len:
            c = self.sock.recv(pkt_len - len(raw))
            if not c:
                break
            raw += c

        if self.compression >= 0:
            uncomp_len, offset = read_varint_from_buf(raw, 0)
            body = raw[offset:]
            if uncomp_len > 0:
                data = zlib.decompress(body)
            else:
                data = body
        else:
            data = raw

        pid, p_offset = read_varint_from_buf(data, 0)
        return pid, data[p_offset:]

    def connect(self, host: str = '127.0.0.1', port: int = 25565):
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.connect((host, port))
        self.running = True

        # Handshake
        bhost = host.encode('utf-8')
        handshake = write_varint(776) + write_varint(len(bhost)) + bhost + struct.pack('>H', port) + write_varint(2)
        self.send_packet(0x00, handshake)

        # Login Start
        bname = self.name.encode('utf-8')
        self.send_packet(0x00, write_varint(len(bname)) + bname + self.uuid.bytes)

        self.thread = threading.Thread(target=self._read_loop, daemon=True)
        self.thread.start()

    def _read_loop(self):
        state = 'LOGIN'
        try:
            while self.running:
                pid, pdata = self.recv_packet()
                if pid is None:
                    break

                if state == 'LOGIN':
                    if pid == 0x03: # Set compression
                        self.compression, _ = read_varint_from_buf(pdata)
                    elif pid == 0x02: # Login finish
                        self.send_packet(0x03, b'') # Login Acknowledged
                        state = 'CONFIG'
                elif state == 'CONFIG':
                    if pid == 0x0C: # Known packs
                        self.send_packet(0x07, write_varint(0))
                    elif pid == 0x04: # Keep alive
                        self.send_packet(0x04, pdata)
                    elif pid == 0x03: # Finish config
                        self.send_packet(0x03, b'')
                        state = 'PLAY'
                        self.in_play = True
                        self.send_packet(0x2C, b'') # Player Loaded
                elif state == 'PLAY':
                    if pid == 0x2C: # Clientbound Keep Alive
                        self.send_packet(0x1C, pdata)
                    elif pid == 0x48: # Clientbound Player Position
                        tp_id, _ = read_varint_from_buf(pdata, 0)
                        self.send_packet(0x00, write_varint(tp_id))
                    elif pid == 0x3B: # Clientbound Open Screen
                        cid, off = read_varint_from_buf(pdata, 0)
                        self.last_container_id = cid
                        record_chat(f"[{self.name}] GUI Opened (screen ID: {cid})")
                    elif pid == 0x12: # Clientbound Container Set Content
                        cid, off = read_varint_from_buf(pdata, 0)
                        state_id, _ = read_varint_from_buf(pdata, off)
                        self.container_state_id = state_id
                    elif pid == 0x79: # Clientbound System Chat
                        try:
                            text = pdata.decode('utf-8', errors='ignore')
                            clean = "".join(c for c in text if c.isprintable())
                            record_chat(f"[{self.name} CHAT] {clean}")
                            if 'eliminated' in clean or 'killed' in clean or 'death.' in clean:
                                # Auto respawn
                                threading.Timer(0.5, self.respawn).start()
                        except:
                            pass
        except Exception as e:
            record_chat(f"[{self.name}] Socket closed: {e}")
        finally:
            self.running = False
            self.in_play = False

    def send_cmd(self, cmd: str):
        if cmd.startswith('/'):
            cmd = cmd[1:]
        bcmd = cmd.encode('utf-8')
        self.send_packet(0x07, write_varint(len(bcmd)) + bcmd)

    def swing(self):
        self.send_packet(0x3F, write_varint(0))

    def respawn(self):
        # SB 0x0C ServerboundClientCommandPacket (Action: 0 = PERFORM_RESPAWN)
        self.send_packet(0x0C, write_varint(0))

    def use_item(self):
        payload = write_varint(0) + write_varint(0) + struct.pack('>ff', 0.0, 0.0)
        self.send_packet(0x43, payload)

    def click_container_slot(self, slot_num: int):
        cid = self.last_container_id or 1
        state_id = self.container_state_id
        # ContainerId(VarInt), StateId(VarInt), Slot(Short), Button(Byte), ContainerInput(VarInt), ChangedSlots(VarInt 0), CarriedItem(0x00)
        payload = (write_varint(cid) +
                   write_varint(state_id) +
                   struct.pack('>hb', slot_num, 0) +
                   write_varint(0) +
                   write_varint(0) +
                   b'\x00')
        self.send_packet(0x12, payload)

    def close(self):
        self.running = False
        if self.sock:
            try:
                self.sock.close()
            except:
                pass


# ── Global State & Chat History ───────────────────────────────────────────────

ACTIVE_BOTS: dict[str, MinecraftBot] = {}
CHAT_HISTORY: list[str] = []

def record_chat(msg: str):
    CHAT_HISTORY.append(msg)
    if len(CHAT_HISTORY) > 100:
        CHAT_HISTORY.pop(0)


# ── MCP Tool Definitions ──────────────────────────────────────────────────────

TOOLS = [
    {
        "name": "mc_status",
        "description": "Get current connection status of all Minecraft bots, last opened GUI, and recent chat history.",
        "inputSchema": {
            "type": "object",
            "properties": {}
        }
    },
    {
        "name": "mc_connect_bot",
        "description": "Connect a bot to the Minecraft 26.2 server. Use is_admin=True for _mr713_ (OP user).",
        "inputSchema": {
            "type": "object",
            "properties": {
                "name": {"type": "string", "description": "Bot username (e.g. _mr713_, bot1, bot2)"},
                "is_admin": {"type": "boolean", "description": "Whether to use admin UUID (0837ed51-0b18-31bd-857f-430260727b38)"}
            },
            "required": ["name"]
        }
    },
    {
        "name": "mc_disconnect_bot",
        "description": "Disconnect a running bot from the server.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "name": {"type": "string", "description": "Bot username"}
            },
            "required": ["name"]
        }
    },
    {
        "name": "mc_send_command",
        "description": "Execute a slash command from a connected bot (e.g. /rdebug start, /op bot1, /rdebug setteam RED bot1).",
        "inputSchema": {
            "type": "object",
            "properties": {
                "bot_name": {"type": "string", "description": "Name of the executing bot (usually _mr713_)"},
                "command": {"type": "string", "description": "Command string to run (e.g. '/rdebug start')"}
            },
            "required": ["bot_name", "command"]
        }
    },
    {
        "name": "mc_pick_card",
        "description": "Pick a card from the draft GUI for a bot (slot_index from 0 to 4, mapped to GUI slots 11..15).",
        "inputSchema": {
            "type": "object",
            "properties": {
                "bot_name": {"type": "string", "description": "Name of the bot picking the card"},
                "card_index": {"type": "integer", "description": "Card index 0, 1, 2, 3, or 4"}
            },
            "required": ["bot_name", "card_index"]
        }
    },
    {
        "name": "mc_bot_action",
        "description": "Trigger an in-game action for a bot: 'swing' (melee swing), 'use_item' (shoot gun / use shield).",
        "inputSchema": {
            "type": "object",
            "properties": {
                "bot_name": {"type": "string", "description": "Bot name"},
                "action": {"type": "string", "enum": ["swing", "use_item"]}
            },
            "required": ["bot_name", "action"]
        }
    },
    {
        "name": "mc_damage_player",
        "description": "Damage or eliminate a player using the Minecraft /damage command via admin.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "target": {"type": "string", "description": "Target player username to damage"},
                "amount": {"type": "number", "description": "Damage amount in half-hearts (e.g. 100 for lethal)"},
                "attacker": {"type": "string", "description": "Optional attacker username (credited killer)"}
            },
            "required": ["target", "amount"]
        }
    },
    {
        "name": "mc_read_chat",
        "description": "Read recent chat messages and server broadcasts.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "limit": {"type": "integer", "description": "Number of recent lines to retrieve (default 20)"}
            }
        }
    }
]

# ── MCP Tool Execution ────────────────────────────────────────────────────────

def handle_tool_call(name: str, args: dict) -> str:
    global ACTIVE_BOTS

    if name == "mc_status":
        bots_info = {
            bname: {
                "in_play": b.in_play,
                "running": b.running,
                "last_container_id": b.last_container_id
            }
            for bname, b in ACTIVE_BOTS.items()
        }
        return json.dumps({
            "connected_bots": bots_info,
            "recent_chat_sample": CHAT_HISTORY[-10:]
        }, indent=2, ensure_ascii=False)

    elif name == "mc_connect_bot":
        bname = args["name"]
        is_admin = args.get("is_admin", bname == "_mr713_")
        if bname in ACTIVE_BOTS and ACTIVE_BOTS[bname].running:
            return f"Bot '{bname}' is already connected."
        bot = MinecraftBot(bname, is_admin)
        bot.connect()
        ACTIVE_BOTS[bname] = bot
        # Wait up to 3s for PLAY state
        for _ in range(15):
            if bot.in_play:
                break
            time.sleep(0.2)
        return f"Bot '{bname}' connected (in_play={bot.in_play})."

    elif name == "mc_disconnect_bot":
        bname = args["name"]
        if bname in ACTIVE_BOTS:
            ACTIVE_BOTS[bname].close()
            del ACTIVE_BOTS[bname]
            return f"Bot '{bname}' disconnected."
        return f"Bot '{bname}' not found."

    elif name == "mc_send_command":
        bname = args["bot_name"]
        cmd = args["command"]
        bot = ACTIVE_BOTS.get(bname)
        if not bot or not bot.in_play:
            return f"Error: bot '{bname}' is not in PLAY state."
        bot.send_cmd(cmd)
        return f"Command '{cmd}' sent from '{bname}'."

    elif name == "mc_pick_card":
        bname = args["bot_name"]
        idx = int(args["card_index"])
        bot = ACTIVE_BOTS.get(bname)
        if not bot or not bot.in_play:
            return f"Error: bot '{bname}' is not in PLAY state."
        # GUI slots are 11, 12, 13, 14, 15
        slots = [11, 12, 13, 14, 15]
        slot = slots[min(max(0, idx), 4)]
        bot.click_container_slot(slot)
        return f"Bot '{bname}' clicked card slot {slot} (card index {idx})."

    elif name == "mc_bot_action":
        bname = args["bot_name"]
        action = args["action"]
        bot = ACTIVE_BOTS.get(bname)
        if not bot or not bot.in_play:
            return f"Error: bot '{bname}' is not in PLAY state."
        if action == "swing":
            bot.swing()
            return f"Bot '{bname}' swung arm."
        elif action == "use_item":
            bot.use_item()
            return f"Bot '{bname}' used item (shot gun)."
        return f"Unknown action: {action}"

    elif name == "mc_damage_player":
        target = args["target"]
        amount = args.get("amount", 100)
        attacker = args.get("attacker")
        admin = ACTIVE_BOTS.get("_mr713_")
        if not admin or not admin.in_play:
            for b in ACTIVE_BOTS.values():
                if b.is_admin and b.in_play:
                    admin = b
                    break
        if not admin:
            return "Error: no admin bot (_mr713_) connected."
        if amount >= 100:
            cmd = f"/kill {target}"
        elif attacker:
            cmd = f"/damage {target} {amount} minecraft:player_attack by {attacker}"
        else:
            cmd = f"/damage {target} {amount} minecraft:generic"
        admin.send_cmd(cmd)
        return f"Executed: {cmd}"

    elif name == "mc_read_chat":
        limit = args.get("limit", 20)
        return "\n".join(CHAT_HISTORY[-limit:])

    return f"Unknown tool: {name}"


# ── MCP Server JSON-RPC Loop ──────────────────────────────────────────────────

def main():
    while True:
        try:
            line = sys.stdin.readline()
            if not line:
                break
            line = line.strip()
            if not line:
                continue

            req = json.loads(line)
            req_id = req.get("id")
            method = req.get("method")
            params = req.get("params", {})

            if method == "initialize":
                res = {
                    "jsonrpc": "2.0",
                    "id": req_id,
                    "result": {
                        "protocolVersion": "2024-11-05",
                        "capabilities": {
                            "tools": {}
                        },
                        "serverInfo": {
                            "name": "minecraft-rounds-mcp",
                            "version": "1.0.0"
                        }
                    }
                }
                sys.stdout.write(json.dumps(res) + "\n")
                sys.stdout.flush()

            elif method == "notifications/initialized":
                pass

            elif method == "ping":
                res = {"jsonrpc": "2.0", "id": req_id, "result": {}}
                sys.stdout.write(json.dumps(res) + "\n")
                sys.stdout.flush()

            elif method == "tools/list":
                res = {
                    "jsonrpc": "2.0",
                    "id": req_id,
                    "result": {
                        "tools": TOOLS
                    }
                }
                sys.stdout.write(json.dumps(res) + "\n")
                sys.stdout.flush()

            elif method == "tools/call":
                tname = params.get("name")
                targs = params.get("arguments", {})
                result_text = handle_tool_call(tname, targs)
                res = {
                    "jsonrpc": "2.0",
                    "id": req_id,
                    "result": {
                        "content": [
                            {"type": "text", "text": str(result_text)}
                        ]
                    }
                }
                sys.stdout.write(json.dumps(res) + "\n")
                sys.stdout.flush()

            else:
                if req_id is not None:
                    res = {
                        "jsonrpc": "2.0",
                        "id": req_id,
                        "error": {"code": -32601, "message": f"Method '{method}' not found"}
                    }
                    sys.stdout.write(json.dumps(res) + "\n")
                    sys.stdout.flush()
        except Exception as e:
            err = {
                "jsonrpc": "2.0",
                "id": None,
                "error": {"code": -32603, "message": str(e)}
            }
            sys.stdout.write(json.dumps(err) + "\n")
            sys.stdout.flush()

if __name__ == "__main__":
    main()
