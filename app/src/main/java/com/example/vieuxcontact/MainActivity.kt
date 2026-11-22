package com.example.vieuxcontact

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.provider.ContactsContract
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button




class MainActivity : AppCompatActivity() {
    private data class ContactItem(
        val id: Long,
        val name: String,
        val favorite: Boolean
    )
    private fun lireNumero(contactId: Long): String {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val colonneNumero = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val selection =
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
        val arguments = arrayOf(contactId.toString())
        val tri = "${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC"

        contentResolver.query(uri, colonneNumero, selection, arguments, tri)?.use { curseur ->
            if (curseur.moveToFirst()) {
                return curseur.getString(0).orEmpty()
            }
        }

        return ""
    }


    private var tousLesContacts: List<ContactItem> = emptyList()

    private val demandePermissionContacts =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { accordee ->
            if (accordee) {
                chargerContacts()
                ouvrirClavier()
            } else {
                Toast.makeText(
                    this,
                    "L'autorisation Contacts est nécessaire pour afficher la liste.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val clavier = insets.getInsets(WindowInsetsCompat.Type.ime())

            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                maxOf(systemBars.bottom, clavier.bottom)
            )

            insets
        }

        configurerRecherche()

        findViewById<Button>(R.id.boutonFermer).setOnClickListener {
            finishAffinity()
        }

        verifierPermissionContacts()
    }

    private fun verifierPermissionContacts() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            chargerContacts()
            ouvrirClavier()
        } else {
            demandePermissionContacts.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun chargerContacts() {
        Thread {
            val contacts = mutableListOf<ContactItem>()

            val colonnes = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.STARRED,
                ContactsContract.Contacts.SORT_KEY_PRIMARY
            )


            val tri = "${ContactsContract.Contacts.STARRED} DESC, " +
                    "${ContactsContract.Contacts.SORT_KEY_PRIMARY} ASC"

            contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                colonnes,
                null,
                null,
                tri
            )?.use { curseur ->
                val colonneNom = curseur.getColumnIndexOrThrow(
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
                )
                val colonneId = curseur.getColumnIndexOrThrow(
                    ContactsContract.Contacts._ID
                )
                val colonneFavori = curseur.getColumnIndexOrThrow(
                    ContactsContract.Contacts.STARRED
                )

                while (curseur.moveToNext()) {
                    val nom = curseur.getString(colonneNom)
                    if (!nom.isNullOrBlank()) {
                        contacts.add(
                            ContactItem(
                                id = curseur.getLong(colonneId),
                                name = nom,
                                favorite = curseur.getInt(colonneFavori) != 0
                            )
                        )

                    }
                }
            }

            runOnUiThread {
                tousLesContacts = contacts
                filtrerContacts()
            }

        }.start()
    }

    private fun afficherContacts(contacts: List<ContactItem>) {
        val liste = findViewById<LinearLayout>(R.id.listeContacts)
        liste.removeAllViews()

        if (contacts.isEmpty()) {
            ajouterLigne(liste, "Aucun contact avec ce nom.", false)
            return
        }

        val favoris = contacts.filter { it.favorite }
        val autres = contacts.filterNot { it.favorite }

        favoris.forEach { ajouterLigne(liste, it.name,false, it.id) }
        autres.forEach { ajouterLigne(liste, it.name, false, it.id) }
    }



    private fun ajouterLigne(liste: LinearLayout, texte: String, titre: Boolean, contactId: Long? = null) {
        val ligne = TextView(this)
        ligne.text = texte
        ligne.gravity = Gravity.CENTER_VERTICAL
        ligne.setTextColor(if (titre) Color.YELLOW else Color.WHITE)
        ligne.textSize = if (titre) 18f else 22f

        if (titre) {
            ligne.setTypeface(null, Typeface.BOLD)
            ligne.setPadding(dp(12), 0, dp(8), 0)
            ligne.setBackgroundColor(Color.TRANSPARENT)
        } else {
            ligne.setPadding(dp(20), 0, dp(12), 0)
            ligne.setBackgroundColor(Color.BLACK)
        }

        val hauteur = if (titre) 40 else 72
        val parametres = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(hauteur)
        )

        if (!titre) {
            parametres.setMargins(0, dp(3), 0, dp(3))
        }
        if (contactId != null) {
            ligne.setOnClickListener {
                val ecranContact = Intent(this, activity_dcontact::class.java)
                ecranContact.putExtra("contact_id", contactId)
                ecranContact.putExtra("contact_name", texte)
                ecranContact.putExtra("contact_phone", lireNumero(contactId))
                startActivity(ecranContact)
            }

        }

        liste.addView(ligne, parametres)
    }

    private fun ouvrirClavier() {
        val champ = findViewById<EditText>(R.id.recherche)
        champ.requestFocus()

        champ.post {
            val clavier = getSystemService(Context.INPUT_METHOD_SERVICE)
                    as InputMethodManager
            clavier.showSoftInput(champ, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun dp(valeur: Int): Int {
        return (valeur * resources.displayMetrics.density).toInt()
    }

    private fun configurerRecherche() {
        val champ = findViewById<EditText>(R.id.recherche)
        champ.setOnClickListener { champ.text.clear() }

        champ.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                filtrerContacts(s?.toString().orEmpty())
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filtrerContacts(texte: String = "") {
        val recherche = texte.trim()

        val resultats = if (recherche.isEmpty()) {
            tousLesContacts
        } else {
            tousLesContacts.filter { contact ->
                contact.name.contains(recherche, ignoreCase = true)
            }
        }

        afficherContacts(resultats)
    }


}