package com.example.hisabkhata

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Ledger(val id:String, var name:String, var createdAt:Long, val entries:MutableList<Entry> = mutableListOf())
data class Entry(val id:String, var account:String, var desc:String, var amount:Double, var type:String, var date:String, var timestamp:Long)

class MainActivity : AppCompatActivity() {
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val ledgers = mutableListOf<Ledger>()
    private val navy = 0xFF10264B.toInt()
    private val navy2 = 0xFF1A3768.toInt()
    private val gold = 0xFFE7B94E.toInt()
    private val bg = 0xFFF1F4FA.toInt()
    private val ink = 0xFF172642.toInt()
    private val green = 0xFF12845D.toInt()
    private val red = 0xFFC43D58.toInt()
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var title: TextView
    private var selected: Ledger? = null
    private var page = "home"
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = navy
        window.navigationBarColor = navy
        buildShell()
        loadData()
    }

    private fun buildShell() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(22, 22, 22, 24); setBackgroundColor(navy)
        }
        title = TextView(this).apply { text="হিসাব খাতা"; textSize=23f; setTextColor(-1); typeface=android.graphics.Typeface.DEFAULT_BOLD }
        header.addView(title)
        content = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(16,18,16,12) }
        val scroll = ScrollView(this).apply { addView(content) }
        val nav = LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER; setPadding(8,8,8,8); setBackgroundColor(0xFFFFFFFF.toInt())
        }
        listOf("খাতা" to "home", "ব্যাকআপ" to "backup").forEach { (label,key) ->
            val b=MaterialButton(this).apply { text=label; isAllCaps=false; setTextColor(navy); setBackgroundColor(0x00FFFFFF) }
            b.setOnClickListener { page=key; selected=null; render() }
            nav.addView(b, LinearLayout.LayoutParams(0,54,1f))
        }
        root.addView(header)
        root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
        root.addView(nav)
        setContentView(root)
    }

    private fun loadData() {
        content.removeAllViews()
        content.addView(label("ডাটা লোড হচ্ছে…", 16f, ink))
        db.collection("accounts").get().addOnSuccessListener { accounts ->
            val loaded = mutableListOf<Ledger>()
            accounts.documents.forEach { d ->
                val n=d.getString("name") ?: d.id
                loaded.add(Ledger(d.id,n,d.getLong("createdAt") ?: 0L))
            }
            db.collection("transactions").get().addOnSuccessListener { txs ->
                txs.documents.forEach { d ->
                    val account=d.getString("account") ?: return@forEach
                    var ledger=loaded.firstOrNull { it.name==account }
                    if (ledger==null) {
                        ledger=Ledger("legacy_"+account.hashCode(),account,d.getLong("timestamp") ?: System.currentTimeMillis())
                        loaded.add(ledger)
                        // Create the missing account record without changing existing records.
                        db.collection("accounts").document(ledger.id).set(mapOf("name" to account,"createdAt" to ledger.createdAt), SetOptions.merge())
                    }
                    ledger.entries.add(Entry(d.getString("id") ?: d.id,account,d.getString("desc") ?: "",
                        d.getDouble("amount") ?: (d.getLong("amount")?.toDouble() ?: 0.0),
                        d.getString("type") ?: "income",d.getString("date") ?: "",d.getLong("timestamp") ?: 0L))
                }
                ledgers.clear(); ledgers.addAll(loaded.sortedByDescending { maxOf(it.createdAt,it.entries.maxOfOrNull { e->e.timestamp } ?: 0L) })
                render()
            }.addOnFailureListener { showError("লেনদেন লোড করা যায়নি") }
        }.addOnFailureListener { showError("খাতা লোড করা যায়নি। Firebase config ও Rules পরীক্ষা করুন।") }
    }

    private fun render() {
        content.removeAllViews()
        if (page=="backup") { title.text="ব্যাকআপ ও রিকভারি"; backupPage(); return }
        if (selected==null) {
            title.text="হিসাব খাতা"
            val income=ledgers.sumOf { it.entries.filter { e->e.type=="income" }.sumOf { e->e.amount } }
            val expense=ledgers.sumOf { it.entries.filter { e->e.type=="expense" }.sumOf { e->e.amount } }
            summary("সর্বমোট নিট জের",income-expense,income,expense)
            section("তোমার হিসাবের খাতা")
            ledgers.forEach { l ->
                val i=l.entries.filter { it.type=="income" }.sumOf { it.amount }
                val e=l.entries.filter { it.type=="expense" }.sumOf { it.amount }
                val card=card()
                val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(16,14,16,14) }
                val names=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
                names.addView(label(l.name,17f,ink,true))
                names.addView(label("${l.entries.size}টি লেনদেন",12f,0xFF77839A.toInt()))
                row.addView(names,LinearLayout.LayoutParams(0,-2,1f))
                row.addView(label(money(i-e),16f,if(i-e<0) red else green,true))
                card.addView(row); card.setOnClickListener { selected=l; render() }; content.addView(card)
            }
            if (ledgers.isEmpty()) content.addView(label("এখনো কোনো খাতা নেই। নতুন খাতা তৈরি করুন।",14f,ink))
            content.addView(actionButton("＋  নতুন খাতা") { accountDialog() })
        } else {
            val l=selected!!
            title.text=l.name
            val i=l.entries.filter { it.type=="income" }.sumOf { it.amount }
            val e=l.entries.filter { it.type=="expense" }.sumOf { it.amount }
            summary("বর্তমান জের",i-e,i,e)
            content.addView(actionButton("＋  নতুন হিসাব") { txDialog(null) })
            content.addView(actionButton("খাতার নাম পরিবর্তন") { accountDialog(l) })
            content.addView(actionButton("খাতা মুছুন",red) { deleteLedger(l) })
            section("লেনদেনের তালিকা")
            l.entries.sortedByDescending { it.timestamp }.forEach { t ->
                val card=card()
                val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(15,12,15,12) }
                val line=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
                line.addView(label(t.desc,15f,ink,true),LinearLayout.LayoutParams(0,-2,1f))
                line.addView(label((if(t.type=="income") "+" else "−")+money(t.amount),15f,if(t.type=="income") green else red,true))
                box.addView(line)
                box.addView(label("${t.date}  •  ${if(t.type=="income") "জমা" else "খরচ"}",12f,0xFF77839A.toInt()))
                val actions=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
                val edit=smallButton("এডিট") { txDialog(t) }
                val del=smallButton("মুছুন") { deleteEntry(t) }
                actions.addView(edit); actions.addView(del); box.addView(actions)
                card.addView(box); content.addView(card)
            }
            content.addView(actionButton("←  সব খাতায় ফিরুন") { selected=null; render() })
        }
    }

    private fun summary(caption:String,balance:Double,income:Double,expense:Double) {
        val c=MaterialCardView(this).apply {
            radius=26f; cardElevation=12f; setCardBackgroundColor(navy2); strokeWidth=0
            setContentPadding(18,18,18,18)
        }
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        box.addView(label(caption,13f,0xFFC5D2EA.toInt()))
        box.addView(label(money(balance),32f,-1,true))
        val stats=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; setPadding(0,14,0,0) }
        val a=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        a.addView(label("মোট জমা",12f,0xFFC5D2EA.toInt())); a.addView(label(money(income),17f,0xFF71E0B4.toInt(),true))
        val b=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        b.addView(label("মোট খরচ",12f,0xFFC5D2EA.toInt())); b.addView(label(money(expense),17f,0xFFFFA1B0.toInt(),true))
        stats.addView(a,LinearLayout.LayoutParams(0,-2,1f)); stats.addView(b,LinearLayout.LayoutParams(0,-2,1f))
        box.addView(stats); c.addView(box); content.addView(c)
    }

    private fun accountDialog(existing:Ledger?=null) {
        val input=EditText(this).apply { setSingleLine(); hint="খাতার নাম"; setText(existing?.name ?: "") }
        AlertDialog.Builder(this).setTitle(if(existing==null) "নতুন খাতা" else "খাতার নাম পরিবর্তন")
            .setView(input).setNegativeButton("বাতিল",null).setPositiveButton("সংরক্ষণ",null).create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val name=input.text.toString().trim()
                        if(name.isBlank()) { input.error="নাম লিখুন"; return@setOnClickListener }
                        if(ledgers.any { it.name.equals(name,true) && it.id!=existing?.id }) { input.error="এই নামে খাতা আছে"; return@setOnClickListener }
                        if(existing==null) {
                            val now=System.currentTimeMillis()
                            val ref=db.collection("accounts").document()
                            ref.set(mapOf("name" to name,"createdAt" to now)).addOnSuccessListener {
                                ledgers.add(Ledger(ref.id,name,now)); dialog.dismiss(); render()
                            }.addOnFailureListener { showError("খাতা সেভ হয়নি") }
                        } else {
                            val batch=db.batch()
                            batch.set(db.collection("accounts").document(existing.id),mapOf("name" to name),SetOptions.merge())
                            existing.entries.forEach { t -> batch.set(db.collection("transactions").document(t.id),mapOf("account" to name),SetOptions.merge()) }
                            batch.commit().addOnSuccessListener {
                                existing.name=name; existing.entries.forEach { it.account=name }; dialog.dismiss(); render()
                            }.addOnFailureListener { showError("নাম পরিবর্তন হয়নি") }
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun txDialog(old:Entry?) {
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(20,4,20,0) }
        val type=RadioGroup(this).apply { orientation=RadioGroup.HORIZONTAL }
        val inc=RadioButton(this).apply { text="জমা"; id=1 }
        val exp=RadioButton(this).apply { text="খরচ"; id=2 }
        type.addView(inc); type.addView(exp); type.check(if(old?.type=="expense") 2 else 1)
        val desc=EditText(this).apply { hint="বিবরণ"; setSingleLine(); setText(old?.desc ?: "") }
        val amount=EditText(this).apply { hint="পরিমাণ (৳)"; inputType=8194; setText(old?.amount?.toString() ?: "") }
        box.addView(type); box.addView(desc); box.addView(amount)
        AlertDialog.Builder(this).setTitle(if(old==null) "নতুন হিসাব" else "হিসাব এডিট").setView(box)
            .setNegativeButton("বাতিল",null).setPositiveButton("সংরক্ষণ",null).create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val d=desc.text.toString().trim(); val a=amount.text.toString().toDoubleOrNull()
                        if(d.isBlank() || a==null || a<=0) { showError("বিবরণ ও সঠিক পরিমাণ দিন"); return@setOnClickListener }
                        val now=old?.timestamp ?: System.currentTimeMillis()
                        val id=old?.id ?: db.collection("transactions").document().id
                        val t=Entry(id,selected!!.name,d,a,if(type.checkedRadioButtonId==2) "expense" else "income",old?.date ?: date(now),now)
                        val data=mapOf("id" to t.id,"account" to t.account,"desc" to t.desc,"amount" to t.amount,"type" to t.type,"date" to t.date,"timestamp" to t.timestamp)
                        db.collection("transactions").document(id).set(data).addOnSuccessListener {
                            if(old==null) selected!!.entries.add(t) else { val idx=selected!!.entries.indexOfFirst { it.id==old.id }; if(idx>=0) selected!!.entries[idx]=t }
                            dialog.dismiss(); render()
                        }.addOnFailureListener { showError("লেনদেন সেভ হয়নি") }
                    }
                }
                dialog.show()
            }
    }

    private fun deleteEntry(t:Entry) {
        AlertDialog.Builder(this).setTitle("লেনদেন মুছবেন?")
            .setMessage("ডাটা রিসাইকেল বিনে রাখা হবে।").setNegativeButton("না",null)
            .setPositiveButton("মুছুন") { _,_ ->
                val payload=JSONObject().apply { put("id",t.id);put("account",t.account);put("desc",t.desc);put("amount",t.amount);put("type",t.type);put("date",t.date);put("timestamp",t.timestamp) }
                val trash=db.collection("trash").document()
                val data=mapOf("kind" to "tx","label" to "${t.desc} · ${money(t.amount)}","deletedAt" to System.currentTimeMillis(),"payload" to payload.toString())
                trash.set(data).continueWithTask { db.collection("transactions").document(t.id).delete() }
                    .addOnSuccessListener { selected?.entries?.removeAll { it.id==t.id }; render() }
                    .addOnFailureListener { showError("মুছতে ব্যর্থ") }
            }.show()
    }

    private fun deleteLedger(l:Ledger) {
        AlertDialog.Builder(this).setTitle("খাতা মুছবেন?")
            .setMessage("খাতা ও এর সব লেনদেন রিসাইকেল বিনে যাবে।").setNegativeButton("না",null)
            .setPositiveButton("মুছুন") { _,_ ->
                val acc=JSONObject().put("id",l.id).put("name",l.name).put("createdAt",l.createdAt)
                val txs=JSONArray()
                l.entries.forEach { t -> txs.put(JSONObject().put("id",t.id).put("account",t.account).put("desc",t.desc).put("amount",t.amount).put("type",t.type).put("date",t.date).put("timestamp",t.timestamp)) }
                val payload=JSONObject().put("account",acc).put("txs",txs)
                val trash=db.collection("trash").document()
                trash.set(mapOf("kind" to "account","label" to l.name,"deletedAt" to System.currentTimeMillis(),"payload" to payload.toString()))
                    .continueWithTask {
                        val batch=db.batch()
                        l.entries.forEach { batch.delete(db.collection("transactions").document(it.id)) }
                        batch.delete(db.collection("accounts").document(l.id)); batch.commit()
                    }.addOnSuccessListener { ledgers.remove(l); selected=null; render() }
                    .addOnFailureListener { showError("খাতা মোছা যায়নি; ডাটা অক্ষত আছে") }
            }.show()
    }

    private fun backupPage() {
        content.addView(label("ব্যাকআপ",19f,ink,true))
        content.addView(label("সব খাতা ও লেনদেন JSON আকারে শেয়ার বা সংরক্ষণ করুন।",14f,ink))
        content.addView(actionButton("ব্যাকআপ তৈরি ও শেয়ার") {
            val accounts=JSONArray(); val txs=JSONArray()
            ledgers.forEach { l ->
                accounts.put(JSONObject().put("id",l.id).put("name",l.name).put("createdAt",l.createdAt))
                l.entries.forEach { t -> txs.put(JSONObject().put("id",t.id).put("account",t.account).put("desc",t.desc).put("amount",t.amount).put("type",t.type).put("date",t.date).put("timestamp",t.timestamp)) }
            }
            val json=JSONObject().put("app","hisab-khata").put("version",2).put("accounts",accounts).put("transactions",txs).toString(2)
            val send=android.content.Intent(android.content.Intent.ACTION_SEND).setType("application/json").putExtra(android.content.Intent.EXTRA_TEXT,json)
            startActivity(android.content.Intent.createChooser(send,"ব্যাকআপ সংরক্ষণ করুন"))
        })
        content.addView(label("রিসাইকেল বিন",19f,ink,true))
        db.collection("trash").get().addOnSuccessListener { docs ->
            content.removeViews(3, maxOf(0,content.childCount-3))
            docs.documents.sortedByDescending { it.getLong("deletedAt") ?: 0L }.forEach { d ->
                val row=card(); val line=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(12,12,12,12) }
                val info=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
                info.addView(label(d.getString("label") ?: "মুছে ফেলা ডাটা",14f,ink,true))
                info.addView(label(d.getString("kind") ?: "",12f,0xFF77839A.toInt()))
                line.addView(info,LinearLayout.LayoutParams(0,-2,1f))
                line.addView(smallButton("ফিরিয়ে আনুন") { recoverTrash(d.id,d.getString("kind") ?: "",d.getString("payload") ?: "") })
                row.addView(line); content.addView(row)
            }
            if(docs.isEmpty()) content.addView(label("রিসাইকেল বিন খালি",14f,ink))
        }.addOnFailureListener { showError("রিসাইকেল বিন লোড হয়নি") }
    }

    private fun recoverTrash(id:String,kind:String,raw:String) {
        try {
            val p=JSONObject(raw); val batch=db.batch()
            if(kind=="tx") {
                val ref=db.collection("transactions").document(p.getString("id"))
                val acc=p.getString("account")
                if(ledgers.none { it.name==acc }) {
                    val ar=db.collection("accounts").document()
                    batch.set(ar,mapOf("name" to acc,"createdAt" to System.currentTimeMillis()))
                }
                batch.set(ref,jsonToMap(p))
            } else {
                val a=p.getJSONObject("account")
                val aid=a.optString("id").ifBlank { db.collection("accounts").document().id }
                batch.set(db.collection("accounts").document(aid),mapOf("name" to a.getString("name"),"createdAt" to a.optLong("createdAt")))
                val arr=p.getJSONArray("txs")
                for(i in 0 until arr.length()) { val t=arr.getJSONObject(i); batch.set(db.collection("transactions").document(t.getString("id")),jsonToMap(t)) }
            }
            batch.delete(db.collection("trash").document(id))
            batch.commit().addOnSuccessListener { loadData() }.addOnFailureListener { showError("ফিরিয়ে আনা যায়নি") }
        } catch(e:Exception) { showError("ব্যাকআপ ডাটা পড়া যায়নি") }
    }

    private fun jsonToMap(o:JSONObject):Map<String,Any> {
        val m=mutableMapOf<String,Any>()
        o.keys().forEach { k -> val v=o.get(k); if(v !is JSONObject && v !is JSONArray && v!=JSONObject.NULL) m[k]=v }
        return m
    }

    private fun card():MaterialCardView = MaterialCardView(this).apply {
        radius=20f; cardElevation=7f; strokeWidth=1; strokeColor=0xFFE2E8F2.toInt()
        setCardBackgroundColor(-1); useCompatPadding=true
        val p=LinearLayout.LayoutParams(-1,-2); p.bottomMargin=11; layoutParams=p
    }
    private fun label(s:String,size:Float,color:Int,bold:Boolean=false)=TextView(this).apply {
        text=s; textSize=size; setTextColor(color); if(bold) typeface=android.graphics.Typeface.DEFAULT_BOLD
        setPadding(2,3,2,3)
    }
    private fun section(s:String) { val t=label(s,16f,ink,true); t.setPadding(3,18,3,10); content.addView(t) }
    private fun actionButton(s:String,color:Int=navy,fn:()->Unit):View =
        MaterialButton(this).apply { text=s; isAllCaps=false; textSize=14f; cornerRadius=18; setBackgroundColor(color); setTextColor(-1); elevation=5f; setOnClickListener { fn() } }
            .also { val p=LinearLayout.LayoutParams(-1,54); p.bottomMargin=10; it.layoutParams=p; content.addView(it) }
    private fun smallButton(s:String,fn:()->Unit)=MaterialButton(this).apply {
        text=s; isAllCaps=false; textSize=11f; minHeight=38; setOnClickListener { fn() }
    }
    private fun money(v:Double)="৳"+String.format(Locale("bn","BD"),"%,.2f",kotlin.math.abs(v)).trimEnd('0').trimEnd('.')
    private fun date(ts:Long)=SimpleDateFormat("dd MMM yyyy, hh:mm a",Locale("bn","BD")).format(Date(ts))
    private fun showError(s:String) { Toast.makeText(this,s,Toast.LENGTH_LONG).show() }
}
