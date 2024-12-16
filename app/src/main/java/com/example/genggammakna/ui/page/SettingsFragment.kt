package com.example.genggammakna.ui.page

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.genggammakna.R
import com.example.genggammakna.StartScreen
import com.example.genggammakna.preferences.UserPreferences

class SettingsFragment : Fragment() {
    private lateinit var userPreferences: UserPreferences
    private lateinit var tvFirstname: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        userPreferences = UserPreferences(requireContext())
        setupGreeting(view)
        setupButtons(view)
        return view
    }
    @SuppressLint("SetTextI18n")
    private fun setupGreeting(view: View) {
        tvFirstname = view.findViewById(R.id.tvGreetingFirstName)
        val username = userPreferences.getUsername() ?: "Guest"
        tvFirstname.text = username
    }
    private fun setupButtons(view: View) {
        view.findViewById<View>(R.id.btnSignIn).setOnClickListener {
            handleSignInOut()
        }
    }
    private fun handleSignInOut() {
        if (userPreferences.isUserSaved()) {
            showLogoutConfirmationDialog()
        } else {
            Toast.makeText(requireContext(), "Redirecting to Login", Toast.LENGTH_SHORT).show()
        }
    }
    private fun showLogoutConfirmationDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setMessage("Are you sure you want to log out?")
            .setCancelable(false)
            .setPositiveButton("Yes") { _, _ ->
                userPreferences.clearUser()
                updateFirstName()
                navigateToStartScreen()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
        val alert = builder.create()
        alert.show()
    }
    @SuppressLint("SetTextI18n")
    private fun updateFirstName() {
        tvFirstname.text = "Hi, Guest"
        Toast.makeText(requireContext(), "Logged out successfully", Toast.LENGTH_SHORT).show()
    }
    private fun navigateToStartScreen() {
        val intent = Intent(requireContext(), StartScreen::class.java)
        startActivity(intent)
        activity?.finish()
    }
}
