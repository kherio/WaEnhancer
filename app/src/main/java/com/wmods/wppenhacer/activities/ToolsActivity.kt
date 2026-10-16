package com.wmods.wppenhacer.activities

import android.os.Bundle
import android.view.MenuItem
import android.widget.FrameLayout
import androidx.fragment.app.FragmentContainerView
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.activities.base.BaseActivity
import com.wmods.wppenhacer.ui.fragments.ToolsFragment

/** Pantalla "Herramientas" de WaToolkit dentro de la app del módulo. */
class ToolsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = FragmentContainerView(this).apply {
            id = R.id.tools_container
            fitsSystemWindows = true
        }
        setContentView(FrameLayout(this).apply { addView(container) })
        setTitle(R.string.toolkit_tab)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.tools_container, ToolsFragment())
                .commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
