package com.tdpham.games.hub.profile

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.tdpham.games.R
import com.tdpham.games.common.SoundManager
import com.tdpham.games.common.profile.ProfileManager
import com.tdpham.games.common.profile.UserProfile
import com.tdpham.games.hub.MainActivity

class ProfileSelectionActivity : AppCompatActivity() {

    private lateinit var profileContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_selection)

        profileContainer = findViewById(R.id.profile_container)
        loadProfiles()
    }

    override fun onResume() {
        super.onResume()
        loadProfiles()
    }

    private fun loadProfiles() {
        profileContainer.removeAllViews()
        val profiles = ProfileManager.getProfiles(this)

        profiles.forEach { profile ->
            addProfileCard(profile)
        }

        addCreateProfileCard()
        
        val activeId = ProfileManager.getActiveProfileId(this)
        var foundFocus = false
        if (activeId != null) {
            for (i in 0 until profileContainer.childCount) {
                val view = profileContainer.getChildAt(i)
                if (view.tag == activeId) {
                    view.requestFocus()
                    foundFocus = true
                    break
                }
            }
        }
        
        if (!foundFocus && profileContainer.childCount > 0) {
            profileContainer.getChildAt(0).requestFocus()
        }
    }

    private fun addProfileCard(profile: UserProfile) {
        val card = LayoutInflater.from(this).inflate(R.layout.item_profile_card, profileContainer, false)
        card.tag = profile.id
        
        card.findViewById<TextView>(R.id.profile_name).text = profile.name
        
        val initialView = card.findViewById<TextView>(R.id.avatar_initial)
        val iconView = card.findViewById<ImageView>(R.id.avatar_icon)
        
        val avatars = listOf(
            R.drawable.ic_hero_knight,
            R.drawable.ic_hero_wizard,
            R.drawable.ic_hero_archer,
            R.drawable.ic_hero_ninja,
            R.drawable.ic_hero_viking,
            R.drawable.ic_hero_dragon,
            R.drawable.ic_hero_phoenix,
            R.drawable.ic_hero_shield,
            R.drawable.ic_hero_sword,
            R.drawable.ic_hero_crown
        )
        
        if (profile.avatarId in avatars.indices) {
            initialView.visibility = View.GONE
            iconView.visibility = View.VISIBLE
            iconView.setImageResource(avatars[profile.avatarId])
            // Color applies to the icon
            iconView.imageTintList = android.content.res.ColorStateList.valueOf(profile.avatarColor)
        } else {
            initialView.visibility = View.VISIBLE
            iconView.visibility = View.GONE
            initialView.text = profile.name.take(1).uppercase()
            // Color applies to initial text
            initialView.setTextColor(profile.avatarColor)
        }
        
        // Background is always neutral
        card.findViewById<View>(R.id.avatar_bg).backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#333333"))
        
        val lockIcon = card.findViewById<ImageView>(R.id.icon_lock)
        lockIcon.visibility = if (profile.pin != null) View.VISIBLE else View.GONE

        card.setOnClickListener {
            handleProfileSelection(profile)
        }

        card.setOnLongClickListener {
            showProfileOptions(profile)
            true
        }

        setupFocusAnimation(card)
        profileContainer.addView(card)
    }

    private fun handleProfileSelection(profile: UserProfile) {
        if (profile.pin != null) {
            showPinDialog(profile)
        } else {
            selectProfile(profile)
        }
    }

    private fun showPinDialog(profile: UserProfile) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_pin_entry)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(true)

        val editPin = dialog.findViewById<EditText>(R.id.edit_pin)
        val errorView = dialog.findViewById<TextView>(R.id.pin_error)

        editPin.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s?.length == 4) {
                    if (s.toString() == profile.pin) {
                        dialog.dismiss()
                        selectProfile(profile)
                    } else {
                        errorView.visibility = View.VISIBLE
                        s.clear()
                        SoundManager.playError()
                    }
                } else {
                    errorView.visibility = View.INVISIBLE
                }
            }
        })

        dialog.show()
        editPin.requestFocus()
    }

    private fun showProfileOptions(profile: UserProfile) {
        if (profile.pin != null) {
            val dialog = android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
            dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            dialog.setContentView(R.layout.dialog_pin_entry)
            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            dialog.setCancelable(true)

            val editPin = dialog.findViewById<EditText>(R.id.edit_pin)
            val errorView = dialog.findViewById<TextView>(R.id.pin_error)
            val titleView = dialog.findViewById<TextView>(R.id.pin_title)
            titleView.text = getString(R.string.edit_profile)

            editPin.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (s?.length == 4) {
                        if (s.toString() == profile.pin) {
                            dialog.dismiss()
                            showOptionsList(profile)
                        } else {
                            errorView.visibility = View.VISIBLE
                            s.clear()
                            SoundManager.playError()
                        }
                    }
                }
            })
            dialog.show()
            editPin.requestFocus()
        } else {
            showOptionsList(profile)
        }
    }

    private fun showOptionsList(profile: UserProfile) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.dialog_background)
            setPadding(28, 28, 28, 28)
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(400, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val title = TextView(this).apply {
            text = profile.name
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }

        val btnEdit = android.widget.Button(this).apply {
            text = getString(R.string.edit_profile)
            setBackgroundResource(R.drawable.bg_btn_green_selector)
            setTextColor(Color.BLACK)
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 50).apply {
                setMargins(0, 0, 0, 10)
            }
            setOnClickListener {
                dialog.dismiss()
                val intent = Intent(this@ProfileSelectionActivity, ProfileCreationActivity::class.java)
                intent.putExtra("EDIT_PROFILE_ID", profile.id)
                startActivity(intent)
            }
        }

        val btnDelete = android.widget.Button(this).apply {
            text = getString(R.string.delete_profile)
            setBackgroundResource(R.drawable.bg_btn_danger_selector)
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 46).apply {
                setMargins(0, 0, 0, 10)
            }
            setOnClickListener {
                dialog.dismiss()
                confirmDelete(profile)
            }
        }

        val btnCancel = android.widget.Button(this).apply {
            text = getString(R.string.back)
            setBackgroundResource(R.drawable.bg_btn_neutral_selector)
            setTextColor(Color.parseColor("#B0BEC5"))
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 44)
            setOnClickListener { dialog.dismiss() }
        }

        btnEdit.nextFocusDownId = btnDelete.id
        btnDelete.nextFocusUpId = btnEdit.id
        btnDelete.nextFocusDownId = btnCancel.id
        btnCancel.nextFocusUpId = btnDelete.id

        view.addView(title)
        view.addView(btnEdit)
        view.addView(btnDelete)
        view.addView(btnCancel)

        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(true)
        dialog.show()
        btnEdit.requestFocus()
    }

    private fun confirmDelete(profile: UserProfile) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.dialog_background)
            setPadding(28, 28, 28, 28)
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(400, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

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
        val btnYes = android.widget.Button(this).apply {
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
                ProfileManager.deleteProfile(this@ProfileSelectionActivity, profile.id)
                val remaining = ProfileManager.getProfiles(this@ProfileSelectionActivity)
                if (remaining.isEmpty()) {
                    startActivity(Intent(this@ProfileSelectionActivity, ProfileCreationActivity::class.java))
                    finish()
                } else {
                    loadProfiles()
                }
            }
        }
        val btnNo = android.widget.Button(this).apply {
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

    private fun addCreateProfileCard() {
        val card = LayoutInflater.from(this).inflate(R.layout.item_profile_card, profileContainer, false)
        card.findViewById<TextView>(R.id.profile_name).text = getString(R.string.add_profile)
        card.findViewById<TextView>(R.id.avatar_initial).text = "+"
        card.findViewById<View>(R.id.avatar_bg).backgroundTintList = android.content.res.ColorStateList.valueOf(Color.GRAY)
        card.findViewById<ImageView>(R.id.icon_lock).visibility = View.GONE

        card.setOnClickListener {
            startActivity(Intent(this, ProfileCreationActivity::class.java))
        }

        setupFocusAnimation(card)
        profileContainer.addView(card)
    }

    private fun selectProfile(profile: UserProfile) {
        ProfileManager.setActiveProfileId(this, profile.id)
        SoundManager.playClick()
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setupFocusAnimation(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                SoundManager.playClick()
                v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200).start()
                v.elevation = 20f
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                v.elevation = 4f
            }
        }
    }
}
