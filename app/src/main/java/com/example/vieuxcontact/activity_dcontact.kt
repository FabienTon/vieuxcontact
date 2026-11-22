package com.example.vieuxcontact

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.ContentValues


class activity_dcontact : AppCompatActivity() {

    private var numeroPourAction = ""

    private val demandePermissionAppel =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { accordee ->
            if (accordee) {
                lancerAppel()
            } else {
                Toast.makeText(
                    this,
                    "Autorisation d'appel refusée",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private fun lancerAppel() {
        val appel = Intent(
            Intent.ACTION_CALL,
            Uri.fromParts("tel", numeroPourAction, null)
        )
        startActivity(appel)
        finishAffinity()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dcontact)

        val nomContact = intent.getStringExtra("contact_name").orEmpty()
        numeroPourAction = intent.getStringExtra("contact_phone").orEmpty()
        val contactId = intent.getLongExtra("contact_id", -1L)

        findViewById<TextView>(R.id.initialContact).text =
            nomContact.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

        val numeroContact = findViewById<TextView>(R.id.numeroContact)
        numeroContact.text = numeroPourAction.ifBlank { "Numéro indisponible" }

        numeroContact.setOnClickListener {
            if (numeroPourAction.isNotBlank()) {
                val pressePapier =
                    getSystemService(CLIPBOARD_SERVICE) as ClipboardManager

                val infosContact = "Nom : $nomContact\nNuméro : $numeroPourAction"
                pressePapier.setPrimaryClip(
                    ClipData.newPlainText("Contact", infosContact)
                )

                Toast.makeText(this, "Contact copié", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Numéro indisponible", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.boutonAppeler).setOnClickListener {
            if (numeroPourAction.isBlank()) {
                Toast.makeText(this, "Numéro indisponible", Toast.LENGTH_SHORT).show()
            } else if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CALL_PHONE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                lancerAppel()
            } else {
                demandePermissionAppel.launch(Manifest.permission.CALL_PHONE)
            }
        }

        findViewById<Button>(R.id.boutonMessage).setOnClickListener {
            if (numeroPourAction.isBlank()) {
                Toast.makeText(this, "Numéro indisponible", Toast.LENGTH_SHORT).show()
            } else {
                val message = Intent(
                    Intent.ACTION_SENDTO,
                    Uri.fromParts("smsto", numeroPourAction, null)
                )

                startActivity(message)
                finishAffinity()
            }
        }

        findViewById<Button>(R.id.boutonFermerDcontact).setOnClickListener {
            finishAffinity()
        }

        contactIdPourFavori = contactId
        estFavori = lireStatutFavori(contactId)

        val boutonFavori = findViewById<Button>(R.id.boutonFavori)
        boutonFavori.text =
            if (estFavori) "Retirer des favoris" else "Ajouter aux favoris"

        boutonFavori.setOnClickListener {
            if (
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                basculerFavori()
            } else {
                demandePermissionFavoris.launch(Manifest.permission.WRITE_CONTACTS)
            }
        }


        findViewById<Button>(R.id.boutonModifier).setOnClickListener {
            if (contactId == -1L) {
                Toast.makeText(this, "Contact introuvable", Toast.LENGTH_SHORT).show()
            } else {
                val contactUri = ContentUris.withAppendedId(
                    ContactsContract.Contacts.CONTENT_URI,
                    contactId
                )

                val modification = Intent(Intent.ACTION_EDIT).apply {
                    setDataAndType(
                        contactUri,
                        ContactsContract.Contacts.CONTENT_ITEM_TYPE
                    )
                }

                try {
                    startActivity(modification)
                    finishAffinity()
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(
                        this,
                        "Aucune application de contacts disponible",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )
            insets
        }
    }
    private var contactIdPourFavori = -1L
    private var estFavori = false

    private val demandePermissionFavoris =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { accordee ->
            if (accordee) {
                basculerFavori()
            } else {
                Toast.makeText(this, "Autorisation de modifier les contacts refusée", Toast.LENGTH_SHORT).show()
            }
        }

    private fun basculerFavori() {
        if (contactIdPourFavori == -1L) {
            Toast.makeText(this, "Contact introuvable", Toast.LENGTH_SHORT).show()
            return
        }

        val nouveauStatut = !estFavori
        val valeurs = ContentValues().apply {
            put(ContactsContract.RawContacts.STARRED, if (nouveauStatut) 1 else 0)
        }

        try {
            val lignesModifiees = contentResolver.update(
                ContactsContract.RawContacts.CONTENT_URI,
                valeurs,
                "${ContactsContract.RawContacts.CONTACT_ID} = ?",
                arrayOf(contactIdPourFavori.toString())
            )

            if (lignesModifiees > 0) {
                estFavori = nouveauStatut
                findViewById<Button>(R.id.boutonFavori).text =
                    if (estFavori) "Retirer des favoris" else "Ajouter aux favoris"
                Toast.makeText(
                    this,
                    if (estFavori) "Ajouté aux favoris" else "Retiré des favoris",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(this, "Le favori n'a pas pu être modifié", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            Toast.makeText(this, "Modification des contacts non autorisée", Toast.LENGTH_SHORT).show()
        }
    }

    private fun lireStatutFavori(contactId: Long): Boolean {
        if (contactId == -1L) return false

        val resultat = contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts.STARRED),
            "${ContactsContract.Contacts._ID} = ?",
            arrayOf(contactId.toString()),
            null
        )

        return resultat?.use { curseur ->
            curseur.moveToFirst() && curseur.getInt(0) != 0
        } ?: false
    }
}
