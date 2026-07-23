package com.tdpham.games.hub.profile

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
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
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_pin_entry, null)
        val editPin = dialogView.findViewById<EditText>(R.id.edit_pin)
        val errorView = dialogView.findViewById<TextView>(R.id.pin_error)

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setView(dialogView)
            .create()

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
    }

    private fun showProfileOptions(profile: UserProfile) {
        if (profile.pin != null) {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_pin_entry, null)
            val editPin = dialogView.findViewById<EditText>(R.id.edit_pin)
            val errorView = dialogView.findViewById<TextView>(R.id.pin_error)
            val titleView = dialogView.findViewById<TextView>(R.id.pin_title)
            titleView.text = getString(R.string.edit_profile)

            val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setView(dialogView)
                .create()

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
        } else {
            showOptionsList(profile)
        }
    }

    private fun showOptionsList(profile: UserProfile) {
        val options = arrayOf(getString(R.string.edit_profile), getString(R.string.delete_profile))
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(profile.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { // Edit
                        val intent = Intent(this, ProfileCreationActivity::class.java)
                        intent.putExtra("EDIT_PROFILE_ID", profile.id)
                        startActivity(intent)
                    }
                    1 -> { // Delete
                        confirmDelete(profile)
                    }
                }
            }
            .show()
    }

    private fun confirmDelete(profile: UserProfile) {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.delete_profile)
            .setMessage(R.string.confirm_delete_profile)
            .setPositiveButton(R.string.yes) { _, _ ->
                ProfileManager.deleteProfile(this, profile.id)
                val remaining = ProfileManager.getProfiles(this)
                if (remaining.isEmpty()) {
                    startActivity(Intent(this, ProfileCreationActivity::class.java))
                    finish()
                } else {
                    loadProfiles()
                }
            }
            .setNegativeButton(R.string.no, null)
            .show()
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
                v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200).start()
                v.elevation = 20f
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                v.elevation = 4f
            }
        }
    }
}
