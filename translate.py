import os
import re

languages = [
    ("values", "en"),
    ("values-ar", "ar"),
    ("values-de", "de"),
    ("values-pt", "pt"),
    ("values-ko", "ko"),
    ("values-hi", "hi"),
    ("values-es", "es"),
    ("values-fr", "fr"),
    ("values-th", "th"),
    ("values-tr", "tr"),
    ("values-vi", "vi"),
    ("values-ja", "ja"),
    ("values-in", "in"),
    ("values-zh-rCN", "zh"),
    ("values-ru", "ru"),
    ("values-it", "it")
]

en_strings = {
    # Monkey Climb
    "monkey_spring": "SPRING",
    "monkey_summer": "SUMMER",
    "monkey_autumn": "AUTUMN",
    "monkey_winter": "WINTER",
    "monkey_death_fell": "FELL OFF SEVERED VINE!",
    "monkey_death_eagle": "CARRIED AWAY BY HARPY EAGLE!",
    "monkey_death_coconut": "HIT BY SPINNING COCONUT!",
    "monkey_death_spider": "BITTEN BY POISONOUS SPIDER!",
    "monkey_death_snake": "BITTEN BY WAVY JUNGLE SNAKE!",
    "monkey_death_bird": "HIT BY FLYING BIRD!",
    "monkey_eagle_warning": "▼ EAGLE WARNING ▼",
    "monkey_beetle_warning": "⚠️ BEETLE",
    "monkey_char_gibbon": "GOLDEN GIBBON",
    "monkey_char_climber": "TARZAN CLIMBER",
    "monkey_char_spider": "SPIDER MONKEY",
    "monkey_char_chimp": "PUNK CHIMP",
    "monkey_char_default": "MONKEY",
    "monkey_jaguar_warning": "▲ JAGUAR BELOW ▲",
    "monkey_death_jaguar": "EATEN BY BLACK JAGUAR!",
    # Retro Racer
    "retro_driver_speed": "Speed: %1$d MPH",
    "retro_driver_final_lap": "FINAL LAP!",
    "retro_driver_lap_count": "LAP %1$d / %2$d",
    "retro_driver_go": "GO!",
    "retro_driver_finish": "FINISH!",
    # Frenzy Feeding
    "frenzy_stage": "STAGE: %1$s (%2$d / %3$d)",
    "frenzy_guppy": "GUPPY",
    "frenzy_angelfish": "ANGELFISH",
    "frenzy_lionfish": "LIONFISH",
    "frenzy_dolphin": "DOLPHIN",
    "frenzy_apex": "APEX KING",
    "frenzy_victory_title": "VICTORY!",
    "frenzy_victory_desc": "You are the King of the Deep Sea!\\nFinal Score: %1$d",
    "frenzy_resume_desc": "Eat smaller fish to grow bigger!\\nBite Shark tails from behind!",
    "frenzy_death_pufferfish": "MOUTH HURT BY INFLATED PUFFERFISH!",
    "frenzy_death_eel": "SHOCKED BY ELECTRIC EEL (STUNNED)!",
    "frenzy_death_lionfish": "POISONED BY LIONFISH (STUNNED)!",
    "frenzy_death_clam": "CANNOT EAT: CLAM IS CLOSED!",
    "frenzy_death_too_large": "CANNOT EAT: %1$s IS TOO LARGE!",
    "frenzy_death_eaten": "EATEN BY %1$s!",
    "frenzy_death_mine": "BLOWN UP BY UNDERSEA MINE!",
    # Fruit Slicer
    "fruit_blade_steel": "STEEL KATANA",
    "fruit_blade_flame": "FLAME BLADE",
    "fruit_blade_cyber": "CYBER GLOW",
    "fruit_blade_shadow": "SHADOW SMOKE",
    "fruit_resume_desc": "Blade: %1$s\\n(Press D-PAD UP/DOWN to swap swords)",
    # Syobon Action
    "syobon_level_clear": "LEVEL %1$d CLEAR!",
    "syobon_next_level": "Press OK/Center for Level %1$d",
    "syobon_victory_title": "YOU ARE A CAT HERO!",
    "syobon_victory_desc": "All Levels Cleared! Press OK/Center to Replay",
    "syobon_retry_hint": "Press OK/Center to Retry",
    "syobon_surprise": "SURPRISE!",
    # Flappy Hero
    "flappy_death_ceiling": "Flew too high and hit the ceiling!",
    "flappy_death_ground": "Crashed into the grassy ground!",
    "flappy_death_pipe": "Crashed into a green pipe!",
    "flappy_death_moving_pipe": "Crashed into a moving pipe!",
    "flappy_death_bat": "Collided with a patrolling bat!",
    "flappy_death_gate": "Chomped by the closing metal gates!",
    "flappy_death_stalactite": "Crushed by a falling stalactite!",
    "flappy_death_mine": "Blew up on a floating spiked mine!",
    "flappy_death_vortex": "Vaporized by the storm vortex core!",
    "flappy_hint_fly_through": "FLY THROUGH",
    "flappy_hint_avoid_bat": "AVOID BAT",
    "flappy_hint_fly_under": "FLY UNDER",
    # Profiles
    "choose_hero": "CHOOSE YOUR HERO",
    "create_hero": "CREATE NEW HERO",
    "enter_hero_name": "ENTER HERO NAME",
    "add_profile": "ADD HERO",
    "switch_profile": "SWITCH HERO",
    "please_enter_name": "Please enter a hero name",
    "setup_pin": "SET HERO LOCK (PIN)",
    "manage_profiles": "MANAGE HEROES",
    "edit_profile": "EDIT HERO",
    "delete_profile": "DELETE HERO",
    "confirm_delete_profile": "Are you sure you want to delete this hero? All scores will be lost.",
    "security_pin_label": "SECURITY PIN",
    "protected_hero": "PROTECTED HERO",
    "guest_hero_name": "GUEST HERO",
    "hero_customization_hint": "Press [OK] on your Name to customize your Hero!"
}

vi_strings = {
    # Monkey Climb
    "monkey_spring": "XUÂN",
    "monkey_summer": "HẠ",
    "monkey_autumn": "THU",
    "monkey_winter": "ĐÔNG",
    "monkey_death_fell": "RƠI KHỎI DÂY BỊ CẮT!",
    "monkey_death_eagle": "BỊ ĐẠI BÀNG BẮT ĐI!",
    "monkey_death_coconut": "BỊ DỪA RƠI TRÚNG!",
    "monkey_death_spider": "BỊ NHỆN ĐỘC CẮN!",
    "monkey_death_snake": "BỊ RẮN RỪNG CẮN!",
    "monkey_death_bird": "BỊ CHIM ĐÂM TRÚNG!",
    "monkey_eagle_warning": "▼ CẢNH BÁO ĐẠI BÀNG ▼",
    "monkey_beetle_warning": "⚠️ BỌ CÁNH CỨNG",
    "monkey_char_gibbon": "VƯỢN VÀNG",
    "monkey_char_climber": "NGƯỜI RỪNG TARZAN",
    "monkey_char_spider": "KHỈ NHỆN",
    "monkey_char_chimp": "TINH TINH NỔI LOẠN",
    "monkey_char_default": "KHỈ",
    "monkey_jaguar_warning": "▲ CẢNH BÁO BÁO ĐEN ▲",
    "monkey_death_jaguar": "BỊ BÁO ĐEN ĂN THỊT!",
    # Retro Racer
    "retro_driver_speed": "Tốc độ: %1$d MPH",
    "retro_driver_final_lap": "VÒNG CUỐI!",
    "retro_driver_lap_count": "VÒNG %1$d / %2$d",
    "retro_driver_go": "CHẠY!",
    "retro_driver_finish": "VỀ ĐÍCH!",
    # Frenzy Feeding
    "frenzy_stage": "CẤP ĐỘ: %1$s (%2$d / %3$d)",
    "frenzy_guppy": "CÁ BẢY MÀU",
    "frenzy_angelfish": "CÁ THẦN TIÊN",
    "frenzy_lionfish": "CÁ SƯ TỬ",
    "frenzy_dolphin": "CÁ HEO",
    "frenzy_apex": "VUA BIỂN SÂU",
    "frenzy_victory_title": "CHIẾN THẮNG!",
    "frenzy_victory_desc": "Bạn là Vua của Biển Sâu!\\nĐiểm số cuối cùng: %1$d",
    "frenzy_resume_desc": "Ăn cá nhỏ hơn để lớn lên!\\nCắn đuôi cá mập từ phía sau!",
    "frenzy_death_pufferfish": "ĐAU MIỆNG VÌ CÁ NÓC!",
    "frenzy_death_eel": "BỊ ĐIỆN GIẬT (TÊ LIỆT)!",
    "frenzy_death_lionfish": "BỊ NHIỄM ĐỘC (TÊ LIỆT)!",
    "frenzy_death_clam": "KHÔNG THỂ ĂN: TRAI ĐANG ĐÓNG!",
    "frenzy_death_too_large": "KHÔNG THỂ ĂN: %1$s QUÁ LỚN!",
    "frenzy_death_eaten": "BỊ %1$s ĂN THỊT!",
    "frenzy_death_mine": "BỊ NỔ BỞI MÌN BIỂN!",
    # Fruit Slicer
    "fruit_blade_steel": "KIẾM THÉP",
    "fruit_blade_flame": "KIẾM LỬA",
    "fruit_blade_cyber": "KIẾM NEON",
    "fruit_blade_shadow": "KIẾM BÓNG TỐI",
    "fruit_resume_desc": "Lưỡi kiếm: %1$s\\n(Nhấn D-PAD LÊN/XUỐNG để đổi kiếm)",
    # Syobon Action
    "syobon_level_clear": "VƯỢT QUA CẤP %1$d!",
    "syobon_next_level": "Nhấn OK/Center để qua Cấp %1$d",
    "syobon_victory_title": "BẠN LÀ MÈO ANH HÙNG!",
    "syobon_victory_desc": "Vượt qua tất cả! Nhấn OK/Center để chơi lại",
    "syobon_retry_hint": "Nhấn OK/Center để thử lại",
    "syobon_surprise": "BẤT NGỜ CHƯA!",
    # Flappy Hero
    "flappy_death_ceiling": "Bay quá cao và đụng trần!",
    "flappy_death_ground": "Rơi xuống đất cỏ!",
    "flappy_death_pipe": "Đâm vào ống xanh!",
    "flappy_death_moving_pipe": "Đâm vào ống di động!",
    "flappy_death_bat": "Va chạm với dơi tuần tra!",
    "flappy_death_gate": "Bị cổng sắt kẹp nát!",
    "flappy_death_stalactite": "Bị thạch nhũ rơi trúng!",
    "flappy_death_mine": "Nổ tung bởi mìn gai!",
    "flappy_death_vortex": "Bị bốc hơi bởi tâm bão!",
    "flappy_hint_fly_through": "BAY XUYÊN QUA",
    "flappy_hint_avoid_bat": "TRÁNH DƠI",
    "flappy_hint_fly_under": "BAY PHÍA DƯỚI",
    # Profiles
    "choose_hero": "CHỌN ANH HÙNG",
    "create_hero": "TẠO ANH HÙNG MỚI",
    "enter_hero_name": "NHẬP TÊN ANH HÙNG",
    "add_profile": "THÊM ANH HÙNG",
    "switch_profile": "ĐỔI ANH HÙNG",
    "please_enter_name": "Vui lòng nhập tên anh hùng",
    "setup_pin": "CÀI KHÓA ANH HÙNG (PIN)",
    "manage_profiles": "QUẢN LÝ ANH HÙNG",
    "edit_profile": "SỬA ANH HÙNG",
    "delete_profile": "XÓA ANH HÙNG",
    "confirm_delete_profile": "Bạn có chắc muốn xóa anh hùng này? Mọi điểm số sẽ bị mất.",
    "security_pin_label": "MÃ PIN BẢO MẬT",
    "protected_hero": "ANH HÙNG BẢO MẬT",
    "guest_hero_name": "ANH HÙNG KHÁCH",
    "hero_customization_hint": "Nhấn [OK] vào Tên để tùy chỉnh Anh hùng!"
}

base_dir = "./app/src/main/res"

for folder, lang in languages:
    file_path = os.path.join(base_dir, folder, "strings.xml")
    if not os.path.exists(file_path):
        print(f"Skipping {file_path}")
        continue
    
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    dict_to_use = vi_strings if lang == "vi" else en_strings
    
    added_something = False
    new_elements = []
    
    for key, val in dict_to_use.items():
        if f'name="{key}"' not in content:
            new_elements.append(f'    <string name="{key}">{val}</string>')
            added_something = True
            
    if added_something:
        match = re.search(r"\s*</resources>", content)
        if match:
            idx = match.start()
            inserted_text = "\n" + "\n".join(new_elements)
            content = content[:idx] + inserted_text + content[idx:]
            with open(file_path, "w", encoding="utf-8") as f:
                f.write(content)
            print(f"Successfully updated: {file_path} with new elements")
        else:
            print(f"Could not find resources tag in {file_path}")
    else:
        print(f"No new elements to add for: {file_path}")
