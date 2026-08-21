package com.tdpham.games.hub.profile

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.tdpham.games.R
import com.tdpham.games.common.SoundManager
import com.tdpham.games.common.profile.ProfileManager
import com.tdpham.games.common.profile.UserProfile
import com.tdpham.games.common.ScoreManager
import com.tdpham.games.hub.MainActivity

class ProfileCreationActivity : AppCompatActivity() {

    private var editingProfileId: String? = null
    private var selectedAvatarId = 0
    private var selectedColor = Color.WHITE
    
    private val avatars = listOf(
        0 to R.drawable.ic_hero_knight,
        1 to R.drawable.ic_hero_wizard,
        2 to R.drawable.ic_hero_archer,
        3 to R.drawable.ic_hero_ninja,
        4 to R.drawable.ic_hero_viking,
        5 to R.drawable.ic_hero_dragon,
        6 to R.drawable.ic_hero_phoenix,
        7 to R.drawable.ic_hero_shield,
        8 to R.drawable.ic_hero_sword,
        9 to R.drawable.ic_hero_crown
    )

    private val colors = listOf(
        "#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5",
        "#2196F3", "#00E5FF", "#009688", "#4CAF50", "#8BC34A",
        "#CDDC39", "#FFEB3B", "#FFD700", "#FF9800", "#FF5722",
        "#FF4081", "#76FF03", "#FFFFFF"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_creation)

        editingProfileId = intent.getStringExtra("EDIT_PROFILE_ID")

        val editName = findViewById<EditText>(R.id.edit_profile_name)
        val editPin = findViewById<EditText>(R.id.edit_profile_pin)
        val pinContainer = findViewById<LinearLayout>(R.id.pin_container)
        val checkProtected = findViewById<CheckBox>(R.id.check_protected_hero)
        val btnCreate = findViewById<Button>(R.id.btn_create_profile)
        val btnCancel = findViewById<Button>(R.id.btn_cancel)
        val btnDelete = findViewById<Button>(R.id.btn_delete_profile)
        val titleView = findViewById<TextView>(R.id.create_hero_title)

        if (editingProfileId != null) {
            val profile = ProfileManager.getProfiles(this).find { it.id == editingProfileId }
            if (profile != null) {
                editName.setText(profile.name)
                editPin.setText(profile.pin)
                checkProtected.isChecked = profile.pin != null
                pinContainer.visibility = if (profile.pin != null) View.VISIBLE else View.GONE
                selectedAvatarId = profile.avatarId
                selectedColor = profile.avatarColor
                btnCreate.text = getString(R.string.save)
                titleView.text = getString(R.string.edit_profile)
                
                btnDelete.visibility = View.VISIBLE
                btnDelete.setOnClickListener {
                    confirmDelete(profile)
                }
            }
        } else {
            selectedColor = Color.parseColor(colors.random())
        }

        checkProtected.setOnCheckedChangeListener { _, isChecked ->
            pinContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (!isChecked) editPin.setText("")
        }

        setupAvatarSelection()
        setupColorSelection()

        btnCreate.setOnClickListener {
            val name = editName.text.toString().trim()
            val pin = editPin.text.toString().trim()
            if (name.isNotEmpty()) {
                if (checkProtected.isChecked && pin.length < 4) {
                    Toast.makeText(this, getString(R.string.pin_digits_hint), Toast.LENGTH_SHORT).show()
                } else {
                    saveProfile(name, if (!checkProtected.isChecked || pin.isEmpty()) null else pin)
                }
            } else {
                Toast.makeText(this, getString(R.string.please_enter_name), Toast.LENGTH_SHORT).show()
            }
        }
        
        btnCancel.setOnClickListener { finish() }
        
        setupFocusEffects(listOf(btnCreate, btnCancel, btnDelete))
    }

    private fun confirmDelete(profile: UserProfile) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val view = layoutInflater.inflate(R.layout.dialog_rate_app, null) as LinearLayout
        view.removeAllViews()
        view.layoutParams = LinearLayout.LayoutParams(400, LinearLayout.LayoutParams.WRAP_CONTENT)
        view.setBackgroundResource(R.drawable.dialog_background)
        view.setPadding(28, 28, 28, 28)
        view.gravity = android.view.Gravity.CENTER_HORIZONTAL

        val title = TextView(this).apply {
            text = getString(R.string.delete_profile)
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 12)
        }
        val msg = TextView(this).apply {
            text = getString(R.string.confirm_delete_profile)
            setTextColor(Color.parseColor("#B0BEC5"))
            textSize = 15f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        val btnYes = Button(this).apply {
            text = getString(R.string.yes)
            setBackgroundResource(R.drawable.bg_btn_danger_selector)
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 48).apply {
                setMargins(0, 0, 0, 10)
            }
            setOnClickListener {
                dialog.dismiss()
                ProfileManager.deleteProfile(this@ProfileCreationActivity, profile.id)
                val intent = Intent(this@ProfileCreationActivity, ProfileSelectionActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }
        val btnNo = Button(this).apply {
            text = getString(R.string.no)
            setBackgroundResource(R.drawable.bg_btn_neutral_selector)
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 46)
            setOnClickListener { dialog.dismiss() }
        }
        btnYes.nextFocusDownId = btnNo.id
        btnNo.nextFocusUpId = btnYes.id

        view.addView(title)
        view.addView(msg)
        view.addView(btnYes)
        view.addView(btnNo)

        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(true)
        dialog.show()
        btnNo.requestFocus()
    }

    private fun setupAvatarSelection() {
        val container = findViewById<LinearLayout>(R.id.avatar_selection_container)
        avatars.forEach { (id, resId) ->
            val iconView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(110, 110).apply { setMargins(12, 0, 12, 0) }
                setImageResource(resId)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(16, 16, 16, 16)
                background = getDrawable(R.drawable.game_item_background)
                backgroundTintList = android.content.res.ColorStateList.valueOf(if (selectedAvatarId == id) Color.parseColor("#33FFFFFF") else Color.parseColor("#1AFFFFFF"))
                imageTintList = android.content.res.ColorStateList.valueOf(if (selectedAvatarId == id) selectedColor else Color.LTGRAY)
                isFocusable = true
                isFocusableInTouchMode = true
                
                setOnClickListener {
                    selectedAvatarId = id
                    updateAvatarSelection()
                    SoundManager.playClick()
                }
                
                setOnFocusChangeListener { view, hasFocus ->
                    if (hasFocus) {
                        view.animate().scaleX(1.18f).scaleY(1.18f).setDuration(180).start()
                        view.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#55FFFFFF"))
                    } else {
                        val isSelected = selectedAvatarId == id
                        view.animate().scaleX(if (isSelected) 1.08f else 1.0f).scaleY(if (isSelected) 1.08f else 1.0f).setDuration(180).start()
                        view.backgroundTintList = android.content.res.ColorStateList.valueOf(if (isSelected) Color.parseColor("#33FFFFFF") else Color.parseColor("#1AFFFFFF"))
                    }
                }
            }
            container.addView(iconView)
        }
    }

    private fun setupColorSelection() {
        val container = findViewById<LinearLayout>(R.id.color_selection_container)
        colors.forEach { hex ->
            val color = Color.parseColor(hex)
            val colorView = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(60, 60).apply { setMargins(12, 0, 12, 0) }
                background = getDrawable(R.drawable.avatar_circle_placeholder)
                backgroundTintList = android.content.res.ColorStateList.valueOf(color)
                isFocusable = true
                isFocusableInTouchMode = true
                
                setOnClickListener {
                    selectedColor = color
                    updateAvatarSelection()
                    updateColorSelection()
                    SoundManager.playClick()
                }
                
                setOnFocusChangeListener { view, hasFocus ->
                    if (hasFocus) {
                        view.animate().scaleX(1.35f).scaleY(1.35f).setDuration(180).start()
                    } else {
                        val isSelected = color == selectedColor
                        view.animate().scaleX(if (isSelected) 1.2f else 1.0f).scaleY(if (isSelected) 1.2f else 1.0f).setDuration(180).start()
                    }
                }
            }
            container.addView(colorView)
        }
        updateColorSelection()
    }

    private fun updateAvatarSelection() {
        val container = findViewById<LinearLayout>(R.id.avatar_selection_container)
        for (i in 0 until container.childCount) {
            val view = container.getChildAt(i) as ImageView
            val id = avatars[i].first
            val isSelected = selectedAvatarId == id
            view.imageTintList = android.content.res.ColorStateList.valueOf(if (isSelected) selectedColor else Color.LTGRAY)
            view.backgroundTintList = android.content.res.ColorStateList.valueOf(if (isSelected) Color.parseColor("#33FFFFFF") else Color.parseColor("#1AFFFFFF"))
            if (!view.hasFocus()) {
                view.scaleX = if (isSelected) 1.08f else 1.0f
                view.scaleY = if (isSelected) 1.08f else 1.0f
            }
        }
    }

    private fun updateColorSelection() {
        val container = findViewById<LinearLayout>(R.id.color_selection_container)
        for (i in 0 until container.childCount) {
            val view = container.getChildAt(i)
            val color = Color.parseColor(colors[i])
            val isSelected = color == selectedColor
            view.alpha = if (isSelected) 1.0f else 0.65f
            if (!view.hasFocus()) {
                view.scaleX = if (isSelected) 1.2f else 1.0f
                view.scaleY = if (isSelected) 1.2f else 1.0f
            }
        }
    }

    private fun saveProfile(name: String, pin: String?) {
        if (editingProfileId != null) {
            val profile = ProfileManager.getProfiles(this).find { it.id == editingProfileId }
            if (profile != null) {
                profile.name = name
                profile.pin = pin
                profile.avatarId = selectedAvatarId
                profile.avatarColor = selectedColor
                ProfileManager.saveProfile(this, profile)
            }
        } else {
            val newProfile = UserProfile(
                name = name,
                avatarColor = selectedColor,
                avatarId = selectedAvatarId,
                pin = pin
            )
            ProfileManager.saveProfile(this, newProfile)
            ProfileManager.setActiveProfileId(this, newProfile.id)
            if (ProfileManager.getProfiles(this).size == 1) {
                ScoreManager.migrateGlobalToProfile(this, newProfile.id)
            }
        }
        
        SoundManager.playSuccess()
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setupFocusEffects(views: List<View>) {
        views.forEach { view ->
            view.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    SoundManager.playClick()
                    v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200).start()
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                }
            }
        }
    }
}
