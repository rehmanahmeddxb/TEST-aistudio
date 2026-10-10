import os
import time

# ==============================================================================
# MTK SERVICING TOOL: TERMINAL CONTROL CENTER
# ==============================================================================

MTK_VID = "0E8D"
HANDSHAKE_CMD = b'\xa0'
EXPECTED_ECHO = b'\x5f'

# Hardware Database
CHIP_DATABASE = {
    "0788": {
        "brand": "Xiaomi",
        "model": "Redmi 9A (MT6765)",
        "chip_name": "MT6765",
        "storage_type": "eMMC",
        "requires_auth": True,
        "partitions": [
            {"name": "preloader", "start": "0x000000000000", "size": "0x000000080000"},
            {"name": "boot",      "start": "0x000000800000", "size": "0x000002000000"},
            {"name": "recovery",  "start": "0x000002800000", "size": "0x000002000000"},
            {"name": "system",    "start": "0x000004800000", "size": "0x0000C0000000"},
            {"name": "userdata",  "start": "0x0000C4800000", "size": "0x000300000000"}
        ]
    }
}

simulated_ports = [{"name": "COM3", "hwid": "USB\\VID_0E8D&PID_0003"}]

# --- HELPER FUNCTIONS ---

def connect_and_identify():
    """Executes Steps 1 to 3: Port Detection, Handshake, and HW Query."""
    print("\n[STEP 1] Scanning for connected hardware...")
    target_port = None
    for port in simulated_ports:
        if MTK_VID in port["hwid"]:
            target_port = port["name"]
            print(f"🎯 Device detected on {target_port}!")
            break
    if not target_port:
        print("❌ Device not found.")
        return None, None

    print(f"\n[STEP 2] Opening {target_port} and sending BROM freeze byte...")
    time.sleep(0.05)
    print("🔒 Handshake complete! Device locked in BROM mode.")

    print("\n[STEP 3] Querying Chip Hardware ID...")
    time.sleep(0.05)
    hex_code = "0788"  # Simulated MT6765 ID
    profile = CHIP_DATABASE.get(hex_code)
    
    if profile:
        print(f"📋 Device Identified: {profile['brand']} {profile['model']}")
        return target_port, profile
    else:
        print("❌ Unknown Hardware ID.")
        return None, None

def get_output_folder(profile):
    """Creates directory named by brand and model."""
    folder_name = f"{profile['brand']}_{profile['model']}".replace(" ", "_").replace("(", "").replace(")", "")
    if not os.path.exists(folder_name):
        os.makedirs(folder_name)
    return folder_name

def handle_security(profile):
    """Executes Step 4: Handles SLA/Auth security requirement."""
    print("\n[STEP 4] Security Check...")
    if profile["requires_auth"]:
        print("⚠️ SLA Security Enabled on this chip!")
        print("  [1] Official .auth File")
        print("  [2] Software SLA Bypass (SRAM Exploit)")
        choice = input("Select Method (1/2): ").strip()
        
        if choice == "1":
            print("🔑 Auth file loaded & verified!")
        elif choice == "2":
            print("⚡ SRAM payload sent! SLA Security disabled!")
        else:
            print("❌ Invalid choice.")
            return False
    return True

# --- MAIN MENU FUNCTIONS ---

def option_1_read_hardware():
    """Option 1: Saves hardware_info.txt and scatter file to folder."""
    port, profile = connect_and_identify()
    if not profile:
        return

    folder = get_output_folder(profile)
    
    # 1. Save hardware_info.txt
    info_path = os.path.join(folder, "hardware_info.txt")
    with open(info_path, "w") as f:
        f.write(f"Brand: {profile['brand']}\n")
        f.write(f"Model: {profile['model']}\n")
        f.write(f"Chipset: {profile['chip_name']}\n")
        f.write(f"Storage: {profile['storage_type']}\n")
    print(f"📄 Saved hardware info: {info_path}")

    # 2. Save Scatter file
    scatter_path = os.path.join(folder, f"{profile['chip_name']}_scatter.txt")
    scatter_content = f"# Scatter file for {profile['model']}\n"
    for part in profile["partitions"]:
        scatter_content += f"- partition_name: {part['name']}\n"
        scatter_content += f"  linear_start_addr: {part['start']}\n"
        scatter_content += f"  partition_size: {part['size']}\n-------------------\n"
    
    with open(scatter_path, "w") as f:
        f.write(scatter_content)
    print(f"📜 Saved scatter file: {scatter_path}")
    print(f"\n🎉 [OPTION 1 COMPLETE] All data saved in directory './{folder}/'")

def option_2_full_dump():
    """Option 2: Runs connection, security bypass, and full eMMC read."""
    port, profile = connect_and_identify()
    if not profile:
        return

    # Security check is mandatory for reading storage
    if not handle_security(profile):
        return

    folder = get_output_folder(profile)
    
    # Calculate Total Capacity
    max_addr = max(int(p["start"], 16) + int(p["size"], 16) for p in profile["partitions"])
    total_mb = max_addr / (1024 * 1024)

    print(f"\n[STEP 5] Reading full eMMC storage space ({total_mb:.2f} MB)...")
    dump_path = os.path.join(folder, "FULL_EMMC_DUMP.bin")
    
    time.sleep(0.1)  # Simulate transfer time
    with open(dump_path, "wb") as f:
        f.write(b'\x00' * 4096)  # Dummy binary data block

    print(f"💾 Full ROM dump saved: {dump_path}")
    print(f"\n🎉 [OPTION 2 COMPLETE] Storage dump saved in directory './{folder}/'")

# --- TERMINAL INTERFACE LOOP ---

def main():
    while True:
        print("\n==========================================================")
        print("   MTK SERVICING TOOL - MAIN CONTROL TERMINAL             ")
        print("==========================================================")
        print("  [1] Read Hardware Data (Save Info & Scatter to Folder)")
        print("  [2] Read Full eMMC ROM Dump (Requires Security Bypass)")
        print("  [3] Exit")
        print("==========================================================")
        
        user_choice = input("Select an option (1-3): ").strip()
        
        if user_choice == "1":
            option_1_read_hardware()
        elif user_choice == "2":
            option_2_full_dump()
        elif user_choice == "3":
            print("\nExiting tool... Goodbye! 👋")
            break
        else:
            print("❌ Invalid entry, please select 1, 2, or 3.")

if __name__ == "__main__":
    main()
