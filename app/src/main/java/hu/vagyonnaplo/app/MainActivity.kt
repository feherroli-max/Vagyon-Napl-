package hu.vagyonnaplo.app

import android.app.DatePickerDialog
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

private val bg = Color(0xFF0D1422)
private val card = Color(0xFF1A2639)
private val accent = Color(0xFF8B91FF)
private val green = Color(0xFF75E4B0)
private val red = Color(0xFFFF8F9D)
private val muted = Color(0xFF9AAAC1)
private fun money(v: Long) = NumberFormat.getIntegerInstance(Locale("hu","HU")).format(v) + " Ft"
private fun signed(v: Long) = (if(v>0) "+" else "") + money(v)
private fun date(t: Long) = SimpleDateFormat("yyyy. MM. dd. HH:mm",Locale("hu","HU")).format(Date(t))
private fun id() = UUID.randomUUID().toString()
private fun iconFor(s: String) = when(s) {
    "bank" -> Icons.Default.AccountBalance; "cash" -> Icons.Default.Payments
    "stock" -> Icons.Default.ShowChart; "saving" -> Icons.Default.Savings
    "home" -> Icons.Default.Home; "coins" -> Icons.Default.Toll
    "card" -> Icons.Default.CreditCard; "work" -> Icons.Default.Work
    "gold" -> Icons.Default.Diamond; "bond" -> Icons.Default.Assessment
    else -> Icons.Default.AccountBalanceWallet
}
private val iconChoices = listOf("bank" to "Bank", "cash" to "Készpénz", "stock" to "TBSZ", "saving" to "Megtakarítás", "home" to "Ingatlan", "coins" to "Érmék", "card" to "Számla", "work" to "Egyéb", "gold" to "Érték", "bond" to "Állampapír", "wallet" to "Tárca")
private val palette = listOf(0xFF8B91FF.toInt(),0xFF75E4B0.toInt(),0xFFFFD07A.toInt(),0xFFFF8F9D.toInt(),0xFF7DCBFF.toInt(),0xFFD7A5FF.toInt())
private fun balance(l: Ledger, person: String, account: String): Long = l.snapshots.filter { it.personId==person && it.entries.any { e -> e.accountId==account } }.maxByOrNull { it.timestamp }?.entries?.find { it.accountId==account }?.value ?: 0L
private fun total(l: Ledger, person: String): Long = l.accounts.filter { it.personId==person }.sumOf { balance(l,person,it.id) }

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent { MaterialTheme(colorScheme=darkColorScheme(primary=accent,background=bg,surface=card,onSurface=Color.White,onBackground=Color.White)) { App() } }
    }
}

@Composable private fun App() {
    val ctx=LocalContext.current
    var unlocked by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var ledger by remember { mutableStateOf(Ledger()) }
    var screen by remember { mutableStateOf("home") }
    var person by remember { mutableStateOf("") }
    // Nem mentett, személyenként külön tárolt űrlapadatok; zároláskor is megmaradnak.
    var drafts by remember { mutableStateOf(mapOf<String,Map<String,String>>()) }
    var notes by remember { mutableStateOf(mapOf<String,String>()) }
    var dates by remember { mutableStateOf(mapOf<String,Long>()) }
    val draft = drafts[person].orEmpty()
    var message by remember { mutableStateOf("") }
    // Egy perc türelmi idő az alkalmazások közötti váltáshoz.
    // A monotón óra az idő/dátum kézi módosításától független.
    var backgroundAt by remember { mutableStateOf<Long?>(null) }
    val lifecycle=LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver { _,event ->
            when(event) {
                Lifecycle.Event.ON_STOP -> {
                    if (unlocked) backgroundAt = SystemClock.elapsedRealtime()
                }
                Lifecycle.Event.ON_START -> {
                    val leftAt = backgroundAt
                    if (unlocked && leftAt != null &&
                        (SystemClock.elapsedRealtime() - leftAt >= 60_000L ||
                         SystemClock.elapsedRealtime() < leftAt)) {
                        unlocked = false
                        password = ""
                        ledger = Ledger()
                        screen = "home"
                    }
                    backgroundAt = null
                }
                else -> Unit
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    fun persist(next: Ledger) { try { Vault.save(ctx,next,password); ledger=next; message="Mentve" } catch(e:Exception) { message="Mentési hiba: ${e.message}" } }
    fun resetDraft(p: String, l: Ledger=ledger) {
        drafts = drafts + (p to l.accounts.filter { it.personId==p && it.active }.associate { it.id to balance(l,p,it.id).toString() })
    }
    fun initDraftIfMissing(p: String, l: Ledger=ledger) {
        if(p !in drafts) resetDraft(p,l)
        else {
            val existing=drafts[p].orEmpty()
            val active=l.accounts.filter { it.personId==p && it.active }
            drafts=drafts+(p to active.associate { it.id to (existing[it.id] ?: balance(l,p,it.id).toString()) })
        }
    }
    if(!unlocked) {
        LockScreen(Vault.exists(ctx), { pass ->
            try {
                if(Vault.exists(ctx)) ledger=Vault.load(ctx,pass) else {
                    ledger=Ledger(people=listOf(Person(id(),"Én")))
                    Vault.save(ctx,ledger,pass)
                }
                password=pass; unlocked=true
                if(ledger.people.none { it.id==person }) person=ledger.people.firstOrNull()?.id.orEmpty()
                initDraftIfMissing(person,ledger); message=""
                true
            } catch(e:Exception) { message="Hibás jelszó vagy sérült adatfájl"; false }
        },message)
        return
    }
    if(person.isEmpty() && ledger.people.isNotEmpty()) { person=ledger.people.first().id; initDraftIfMissing(person) }
    Scaffold(containerColor=bg,bottomBar={ NavigationBar(containerColor=card) {
        listOf(Triple("home","Vagyon",Icons.Default.AccountBalanceWallet),Triple("stats","Statisztika",Icons.Default.ShowChart),Triple("history","Előzmények",Icons.Default.History),Triple("settings","Beállítások",Icons.Default.Settings)).forEach { (s,label,ico) ->
            NavigationBarItem(selected=screen==s,onClick={screen=s},icon={Icon(ico,null)},label={Text(label,fontSize=10.sp)})
        }
    } }, snackbarHost={ if(message.isNotBlank()) LaunchedEffect(message) { kotlinx.coroutines.delay(2600); message="" } }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            when(screen) {
                "home" -> Home(ledger,person,draft,
                    notes[person].orEmpty(),dates[person] ?: System.currentTimeMillis(),
                    {person=it;initDraftIfMissing(it)},
                    {a,v->drafts=drafts+(person to (drafts[person].orEmpty()+(a to v)))},
                    {notes=notes+(person to it)}, {dates=dates+(person to it)},
                    {persist(it);resetDraft(person,it);notes=notes+(person to "");dates=dates+(person to System.currentTimeMillis())},
                    {persist(it);initDraftIfMissing(person,it)})
                "stats" -> Stats(ledger)
                "history" -> History(ledger,person,{person=it;initDraftIfMissing(it)},{persist(it)})
                "settings" -> Settings(ledger,password,{persist(it);if(!it.people.any { p->p.id==person }) { person=it.people.firstOrNull()?.id.orEmpty() };resetDraft(person)},{password=it}, {unlocked=false;password="";ledger=Ledger();drafts=emptyMap();notes=emptyMap();dates=emptyMap();person=""}, {message=it})
            }
        }
    }
    if(message.isNotBlank()) Box(Modifier.fillMaxSize().padding(bottom=86.dp),contentAlignment=Alignment.BottomCenter) { Surface(color=Color(0xFF34455E),shape=RoundedCornerShape(12.dp)) { Text(message,Modifier.padding(14.dp),color=Color.White) } }
}

@Composable private fun LockScreen(exists:Boolean, onUnlock:(String)->Boolean, error:String) {
    var pw by remember { mutableStateOf("") }; var confirm by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(bg).padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
        Icon(Icons.Default.Lock,tint=accent,contentDescription=null,modifier=Modifier.size(60.dp))
        Spacer(Modifier.height(22.dp)); Text(if(exists) "Üdvözlünk újra!" else "Vagyon Napló beállítása",fontSize=25.sp,fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(10.dp)); Text(if(exists) "Add meg az alkalmazás saját jelszavát" else "Hozz létre egy saját, legalább 8 karakteres jelszót",color=muted)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(pw,{pw=it},label={Text("Jelszó")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
        if(!exists) { Spacer(Modifier.height(10.dp)); OutlinedTextField(confirm,{confirm=it},label={Text("Jelszó megerősítése")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth()) }
        Spacer(Modifier.height(16.dp))
        Button(onClick={if(exists || (pw.length>=8 && pw==confirm)) { if(onUnlock(pw)) { pw="";confirm="" } } },enabled=pw.length >= (if(exists) 1 else 8) && (exists || pw==confirm),modifier=Modifier.fillMaxWidth()) { Text(if(exists) "Feloldás" else "Jelszó létrehozása") }
        if(error.isNotBlank()) Text(error,color=red,modifier=Modifier.padding(10.dp))
        if(!exists) Text("A jelszó nélkül az adatok nem állíthatók helyre. Őrizd meg biztonságos helyen!",color=muted,fontSize=12.sp,modifier=Modifier.padding(top=20.dp))
    }
}

@Composable private fun Heading(title:String,subtitle:String="") { Column(Modifier.padding(horizontal=20.dp,vertical=16.dp)) { Text(title,fontSize=26.sp,fontWeight=FontWeight.Bold); if(subtitle.isNotBlank()) Text(subtitle,color=muted,fontSize=12.sp) } }
@Composable private fun PersonTabs(l:Ledger,selected:String,onSelect:(String)->Unit,all:Boolean=false) {
    val opts=(if(all) listOf("ALL" to "Család összesen") else emptyList()) + l.people.map { it.id to it.name }
    androidx.compose.foundation.lazy.LazyRow(contentPadding=PaddingValues(horizontal=18.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        items(opts) { (pid,name) -> FilterChip(selected=selected==pid,onClick={onSelect(pid)},label={Text(name)}) }
    }
}
@Composable private fun Metric(title:String,value:String,highlight:Color=Color.White) {
    Column(Modifier.background(card,RoundedCornerShape(18.dp)).padding(18.dp)) { Text(title,color=muted,fontSize=13.sp); Spacer(Modifier.height(6.dp)); Text(value,fontSize=23.sp,fontWeight=FontWeight.Bold,color=highlight) }
}
@Composable private fun Home(l:Ledger,person:String,draft:Map<String,String>,note:String,chosenDate:Long,
    onPerson:(String)->Unit,onDraft:(String,String)->Unit,onNote:(String)->Unit,onDate:(Long)->Unit,
    onSave:(Ledger)->Unit,onCategory:(Ledger)->Unit) {
    val accounts=l.accounts.filter { it.personId==person && it.active }
    val old=total(l,person)
    val current=accounts.sumOf { (draft[it.id]?.toLongOrNull() ?: 0L) }
    val diff=current-old
    val context = LocalContext.current
    fun chooseDate() {
        val calendar = Calendar.getInstance().apply { timeInMillis = chosenDate }
        DatePickerDialog(context, { _, year, month, day ->
            onDate(Calendar.getInstance().apply {
                timeInMillis = chosenDate
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, day)
            }.timeInMillis)
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).apply {
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }
    var edit by remember { mutableStateOf<Account?>(null) }
    var add by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var warning by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Heading("Vagyonom","Aktuális pénzügyi áttekintés")
        PersonTabs(l,person,onPerson)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.padding(horizontal=18.dp)) {
            Metric("Jelenlegi összeg",money(current))
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Text("Pénzeszközök",fontWeight=FontWeight.SemiBold,fontSize=18.sp)
                TextButton(onClick={add=true}) { Icon(Icons.Default.Add,null);Text("Új kategória") }
            }
            accounts.forEach { a ->
                Row(Modifier.fillMaxWidth().padding(bottom=9.dp).background(card,RoundedCornerShape(16.dp)).padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(iconFor(a.icon),null,tint=Color(a.color),modifier=Modifier.size(26.dp)); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Text(a.name,fontWeight=FontWeight.Medium); Text("Ft",color=muted,fontSize=11.sp) }
                    OutlinedTextField(value=draft[a.id] ?: "0",onValueChange={if(it.isEmpty() || (it.all(Char::isDigit) && it.length<=15)) onDraft(a.id,it)},modifier=Modifier.width(137.dp),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),textStyle=LocalTextStyle.current.copy(fontSize=15.sp))
                    IconButton(onClick={edit=a}) { Icon(Icons.Default.Edit,null,tint=muted) }
                }
            }
            if(accounts.isEmpty()) Text("Adj hozzá egy pénzkategóriát az első mentéshez.",color=muted,modifier=Modifier.padding(12.dp))
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().background(card,RoundedCornerShape(16.dp)).padding(16.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("Előző mentés",color=muted);Text(money(old)) }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("Különbözet",color=muted);Text(signed(diff),fontWeight=FontWeight.Bold,color=if(diff>0)green else if(diff<0)red else Color.White) }
                if(old>0) Text(String.format(Locale("hu","HU"),"%+.2f%%",100.0*diff/old),color=if(diff>=0)green else red,fontSize=13.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
                Text("Rögzítés dátuma",color=muted)
                TextButton(onClick={chooseDate()}) { Icon(Icons.Default.CalendarMonth,null); Spacer(Modifier.width(6.dp)); Text(SimpleDateFormat("yyyy. MM. dd.",Locale("hu","HU")).format(Date(chosenDate))) }
            }
            OutlinedTextField(note,onNote,label={Text("Megjegyzés (opcionális)")},placeholder={Text("Pl. ablakcsere")},modifier=Modifier.fillMaxWidth(),maxLines=3)
            Spacer(Modifier.height(12.dp))
            Button(onClick={
                if(accounts.isEmpty()) warning="Előbb hozz létre legalább egy kategóriát"
                else if(accounts.any { draft[it.id].isNullOrBlank() }) warning="Minden kategóriában adj meg összeget (akár 0 Ft-ot)"
                else confirm=true
            },modifier=Modifier.fillMaxWidth().height(54.dp)) { Icon(Icons.Default.Save,null); Spacer(Modifier.width(8.dp));Text("Állapot mentése") }
            Spacer(Modifier.height(24.dp))
        }
    }
    if(warning.isNotBlank()) AlertDialog(onDismissRequest={warning=""},confirmButton={TextButton(onClick={warning=""}){Text("OK")}},title={Text("Figyelem")},text={Text(warning)})
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("Állapot rögzítése")},text={Column { Text("Dátum: ${date(chosenDate)}");Text("Új állomány: ${money(current)}");Text("Változás: ${signed(diff)}",color=if(diff<0)red else green);if(note.isNotBlank()) Text("Megjegyzés: $note") }},confirmButton={TextButton(onClick={
        val snap=Snapshot(id(),person,chosenDate,note,l.accounts.filter { it.personId==person }.map { a -> Entry(a.id,if(a.active) draft[a.id]!!.toLong() else balance(l,person,a.id)) })
        onSave(l.copy(snapshots=l.snapshots+snap));confirm=false
    }) { Text("Mentés") }},dismissButton={TextButton(onClick={confirm=false}){Text("Mégse")}})
    if(add || edit!=null) AccountEditor(edit,onDismiss={add=false;edit=null},onApply={updated->
        val originalId=edit?.id
        val next=if(originalId==null) l.copy(accounts=l.accounts+updated.copy(id=id(),personId=person)) else l.copy(accounts=l.accounts.map { if(it.id==originalId) updated else it })
        onCategory(next);add=false;edit=null
    },onArchive={ a ->
        if((draft[a.id]?.toLongOrNull() ?: balance(l,person,a.id))==0L) {
            onCategory(l.copy(accounts=l.accounts.map { if(it.id==a.id) it.copy(active=false) else it }));edit=null
        } else warning="Csak 0 Ft-os kategóriát lehet archiválni"
    })
}

@Composable private fun AccountEditor(existing:Account?,onDismiss:()->Unit,onApply:(Account)->Unit,onArchive:(Account)->Unit) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var icon by remember(existing?.id) { mutableStateOf(existing?.icon ?: "bank") }
    var color by remember(existing?.id) { mutableIntStateOf(existing?.color ?: palette[0].toInt()) }
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(existing==null) "Új kategória" else "Kategória szerkesztése")},text={Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState())) {
        OutlinedTextField(name,{name=it},label={Text("Kategória neve")},singleLine=true)
        Spacer(Modifier.height(14.dp));Text("Ikon",fontWeight=FontWeight.SemiBold)
        iconChoices.chunked(4).forEach { row -> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) { row.forEach { (key,label) -> Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.width(64.dp).clickable { icon=key }.padding(6.dp)) { Icon(iconFor(key),label,tint=if(icon==key)accent else muted);Text(label,fontSize=9.sp,color=if(icon==key)accent else muted) } } } }
        Spacer(Modifier.height(12.dp));Text("Szín",fontWeight=FontWeight.SemiBold)
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { palette.forEach { c -> Box(Modifier.size(if(color==c.toInt())32.dp else 26.dp).background(Color(c),RoundedCornerShape(50)).clickable { color=c.toInt() }) } }
        if(existing!=null) TextButton(onClick={onArchive(existing)}) { Text("Kategória archiválása (csak 0 Ft esetén)",color=red) }
    }},confirmButton={TextButton(onClick={onApply(existing?.copy(name=name.trim(),icon=icon,color=color) ?: Account("","",name.trim(),icon,color))},enabled=name.isNotBlank()){Text("Mentés")}},dismissButton={TextButton(onClick=onDismiss){Text("Mégse")}})
}

private data class Point(val time:Long,val amount:Long,val note:String)
private fun timeline(l:Ledger,person:String,account:String,start:Long,end:Long):List<Point> {
    val selected=if(person=="ALL") l.people.map { it.id }.toSet() else setOf(person)
    val matching=l.accounts.filter { it.personId in selected && (account=="ALL" || it.name==account) }
    val relevantAccounts=matching.map { it.id }.toSet()
    // Kategóriára szűrve csak azok a személyek számítanak, akiknek van ilyen kategóriájuk.
    val contributors=matching.map { it.personId }.toSet()
    if(contributors.isEmpty()) return emptyList()
    fun dayStart(timestamp:Long):Long = Calendar.getInstance().apply {
        timeInMillis=timestamp
        set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)
    }.timeInMillis
    val from=dayStart(start)
    val until=dayStart(end)
    val relevant=l.snapshots.filter { snap ->
        snap.personId in contributors && snap.timestamp<=end &&
            snap.entries.any { it.accountId in relevantAccounts }
    }.sortedWith(compareBy<Snapshot> { it.timestamp }.thenBy { it.id })
    if(relevant.isEmpty()) return emptyList()
    // Naponként pontosan egy pont: a nap végéig ismert legutolsó állapot személyenként.
    // Amíg nincs minden érintett személyhez ismert állapot, nem rajzolunk hiányzó adatból 0 Ft-ot.
    val days=relevant.map { dayStart(it.timestamp) }.filter { it in from..until }.distinct().sorted()
    val openingKnown=contributors.all { pid -> relevant.any { it.personId==pid && it.timestamp<from } }
    val plotDays=(if(openingKnown) listOf(from) else emptyList())+days
    return plotDays.distinct().sorted().mapNotNull { day ->
        val nextDay=Calendar.getInstance().apply { timeInMillis=day;add(Calendar.DAY_OF_MONTH,1) }.timeInMillis
        val cutoff=if(day==from && openingKnown) from else nextDay
        val latestByPerson=contributors.associateWith { pid ->
            relevant.lastOrNull { it.personId==pid && it.timestamp<cutoff }
        }
        if(latestByPerson.values.any { it==null }) return@mapNotNull null
        val total=latestByPerson.values.filterNotNull().sumOf { snapshot ->
            snapshot.entries.filter { it.accountId in relevantAccounts }.sumOf { it.value }
        }
        val note=if(day==from && openingKnown) "" else relevant.filter {
            dayStart(it.timestamp)==day && it.note.isNotBlank()
        }.joinToString("; ") { it.note }
        Point(day,total,note)
    }
}
private data class PlotSeries(val label:String,val color:Color,val points:List<Point>)
private val chartColors=listOf(Color(0xFF7DCBFF),Color(0xFFFFD07A),Color(0xFFD7A5FF),Color(0xFF75E4B0),Color(0xFFFF8F9D),Color(0xFF8B91FF))
private fun axisMoney(v:Double):String = when {
    kotlin.math.abs(v)>=1_000_000 -> String.format(Locale("hu","HU"),"%.1f M",v/1_000_000)
    kotlin.math.abs(v)>=1_000 -> String.format(Locale("hu","HU"),"%.0f e",v/1_000)
    else -> String.format(Locale("hu","HU"),"%.0f",v)
}
@Composable private fun ChartView(series:List<PlotSeries>) {
    val visible=series.filter { it.points.isNotEmpty() }
    if(visible.isEmpty()) return
    val all=visible.flatMap { it.points }
    val low=all.minOf { it.amount }.toDouble()
    val high=all.maxOf { it.amount }.toDouble()
    val padding=(high-low).coerceAtLeast(kotlin.math.abs(high)*0.03).coerceAtLeast(1.0)*0.12
    val min=low-padding;val max=high+padding
    val first=all.minOf { it.time };val last=all.maxOf { it.time }
    val duration=(last-first).coerceAtLeast(1L).toDouble()
    val formatter=SimpleDateFormat("MM.dd",Locale("hu","HU"))
    Canvas(Modifier.fillMaxWidth().height(265.dp).background(card,RoundedCornerShape(16.dp)).padding(10.dp)) {
        val left=58.dp.toPx();val right=12.dp.toPx();val top=12.dp.toPx();val bottom=28.dp.toPx()
        val w=(size.width-left-right).coerceAtLeast(1f)
        val h=(size.height-top-bottom).coerceAtLeast(1f)
        val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color=android.graphics.Color.rgb(185,196,214);textSize=10.sp.toPx()
        }
        for(i in 0..4) {
            val y=top+h*i/4f
            drawLine(Color(0xFF344056),Offset(left,y),Offset(left+w,y),strokeWidth=1.dp.toPx())
            paint.textAlign=android.graphics.Paint.Align.RIGHT
            drawContext.canvas.nativeCanvas.drawText(axisMoney(max-(max-min)*i/4),left-6.dp.toPx(),y+3.dp.toPx(),paint)
        }
        val ticks=if(first==last) 1 else 4
        paint.textAlign=android.graphics.Paint.Align.CENTER
        for(i in 0..ticks) {
            val fraction=if(ticks==1) 0.5 else i.toDouble()/ticks
            val x=left+w*fraction.toFloat()
            val t=first+(duration*fraction).toLong()
            drawContext.canvas.nativeCanvas.drawText(formatter.format(Date(t)),x,top+h+18.dp.toPx(),paint)
        }
        visible.forEach { line ->
            val coords=line.points.sortedBy { it.time }.map { p ->
                Offset(left+((p.time-first)/duration*w).toFloat(),top+((max-p.amount)/(max-min)*h).toFloat())
            }
            coords.zipWithNext().forEach { (p,q) -> drawLine(line.color,p,q,strokeWidth=2.5.dp.toPx(),cap=StrokeCap.Round) }
            coords.forEach { drawCircle(line.color,radius=3.dp.toPx(),center=it) }
        }
    }
    Column(Modifier.fillMaxWidth().padding(top=10.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
        visible.chunked(2).forEach { row ->
            Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                row.forEach { item ->
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(item.color,RoundedCornerShape(50)))
                        Spacer(Modifier.width(6.dp))
                        Text(item.label,fontSize=12.sp,color=muted)
                    }
                }
            }
        }
    }
}
@Composable private fun DateButton(label:String,ts:Long,onPick:(Long)->Unit) {
    val ctx=LocalContext.current
    OutlinedButton(onClick={val c=Calendar.getInstance().apply { timeInMillis=ts };DatePickerDialog(ctx,{_,y,m,d-> val n=Calendar.getInstance().apply { set(y,m,d,0,0,0);set(Calendar.MILLISECOND,0) };onPick(n.timeInMillis) },c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show()}) {
        Icon(Icons.Default.DateRange,null,modifier=Modifier.size(16.dp));Spacer(Modifier.width(5.dp));Text("$label: "+SimpleDateFormat("yyyy.MM.dd",Locale("hu","HU")).format(Date(ts)),fontSize=12.sp)
    }
}
@Composable private fun Stats(l:Ledger) {
    var person by remember { mutableStateOf("ALL") }
    var account by remember { mutableStateOf("ALL") }
    var period by remember { mutableStateOf("MONTH") }
    val now=System.currentTimeMillis()
    val monthStart=Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH,1);set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0) }.timeInMillis
    var start by remember { mutableLongStateOf(monthStart) }
    var end by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var menu by remember { mutableStateOf(false) }
    val names=l.accounts.filter { person=="ALL" || it.personId==person }.map { it.name }.distinct().sorted()
    val points=remember(l,person,account,start,end) { timeline(l,person,account,start,end+86_399_999L) }
    val chartSeries=remember(l,person,account,start,end) {
        if(person=="ALL" && account=="ALL") {
            l.people.mapIndexed { index,p ->
                PlotSeries(p.name,chartColors[index%chartColors.size],timeline(l,p.id,"ALL",start,end+86_399_999L))
            } + PlotSeries("Család összesen",accent,points)
        } else {
            listOf(PlotSeries(if(person=="ALL") "Család összesen" else (l.people.find { it.id==person }?.name ?: "Kiválasztott személy"),accent,points))
        }
    }
    val opening=points.firstOrNull()?.amount ?: 0L;val closing=points.lastOrNull()?.amount ?: 0L;val delta=closing-opening
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Heading("Statisztika","Családi összehasonlítás vagy egy személy grafikonja")
        PersonTabs(l,person,{person=it;account="ALL"},all=true)
        Column(Modifier.padding(18.dp)) {
            Box { OutlinedButton(onClick={menu=true},modifier=Modifier.fillMaxWidth()) { Icon(Icons.Default.FilterList,null);Spacer(Modifier.width(8.dp));Text(if(account=="ALL")"Összes kategória" else account) }
                DropdownMenu(expanded=menu,onDismissRequest={menu=false}) { (listOf("ALL" to "Összes kategória")+names.map { it to it }).forEach { (key,label)->DropdownMenuItem(text={Text(label)},onClick={account=key;menu=false}) } }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf("MONTH" to "Aktuális hónap", "YEAR" to "1 év", "CUSTOM" to "Egyéni").forEach { (key,label) ->
                    FilterChip(selected=period==key,onClick={
                        period=key
                        if(key=="MONTH") {
                            start=Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH,1);set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0) }.timeInMillis
                            end=System.currentTimeMillis()
                        } else if(key=="YEAR") {
                            val cal=Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0);add(Calendar.YEAR,-1) }
                            start=cal.timeInMillis
                            end=System.currentTimeMillis()
                        }
                    },label={Text(label,fontSize=11.sp)})
                }
            }
            DateButton("Kezdet",start,{start=it;period="CUSTOM"})
            DateButton("Vége",end,{end=it;period="CUSTOM"})
            if(start>end) { Text("A kezdődátum nem lehet későbbi a záródátumnál.",color=red);return@Column }
            Spacer(Modifier.height(10.dp))
            Metric("Változás a kiválasztott időszakban",if(points.isEmpty()) "Nincs adat" else signed(delta),if(delta>0)green else if(delta<0)red else Color.White)
            Spacer(Modifier.height(8.dp))
            if(points.isNotEmpty() && opening!=0L) Text(String.format(Locale("hu","HU"),"%+.2f%%",delta*100.0/opening),color=if(delta>=0)green else red)
            Spacer(Modifier.height(14.dp))
            ChartView(chartSeries)
            Row(Modifier.fillMaxWidth().padding(top=12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { Metric("Nyitó állomány",money(opening)) }
                Box(Modifier.weight(1f)) { Metric("Záró állomány",money(closing)) }
            }
            Spacer(Modifier.height(14.dp))
            Text("Megjegyzéssel jelölt változások",fontWeight=FontWeight.SemiBold)
            points.filter { it.note.isNotBlank() }.reversed().forEach { p ->
                Column(Modifier.fillMaxWidth().padding(top=8.dp).background(card,RoundedCornerShape(12.dp)).padding(12.dp)) { Text(date(p.time),color=muted,fontSize=12.sp);Text(p.note);Text(money(p.amount),color=accent) }
            }
            if(points.isEmpty()) Text("A kiválasztott időszakban még nincs rögzített adat. A grafikon az első tényleges mentéstől indul.",color=muted,modifier=Modifier.padding(12.dp))
        }
    }
}

@Composable private fun History(l:Ledger,person:String,onPerson:(String)->Unit,onSave:(Ledger)->Unit) {
    var edit by remember { mutableStateOf<Snapshot?>(null) }
    var delete by remember { mutableStateOf<Snapshot?>(null) }
    Column(Modifier.fillMaxSize()) {
        Heading("Előzmények","Korábbi mentések és megjegyzések")
        PersonTabs(l,person,onPerson)
        LazyColumn(contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            items(l.snapshots.filter { it.personId==person }.sortedByDescending { it.timestamp }) { s ->
                Column(Modifier.fillMaxWidth().background(card,RoundedCornerShape(16.dp)).padding(14.dp)) {
                    Text(date(s.timestamp),color=muted,fontSize=12.sp)
                    Spacer(Modifier.height(5.dp));Text(money(s.entries.sumOf { it.value }),fontSize=21.sp,fontWeight=FontWeight.Bold)
                    if(s.note.isNotBlank()) Text(s.note,color=accent,modifier=Modifier.padding(top=4.dp))
                    Row(horizontalArrangement=Arrangement.End,modifier=Modifier.fillMaxWidth()) {
                        TextButton(onClick={edit=s}) { Icon(Icons.Default.Edit,null,modifier=Modifier.size(17.dp));Text("Javítás") }
                        TextButton(onClick={delete=s}) { Icon(Icons.Default.Delete,null,tint=red,modifier=Modifier.size(17.dp));Text("Törlés",color=red) }
                    }
                }
            }
        }
    }
    if(delete!=null) AlertDialog(onDismissRequest={delete=null},title={Text("Mentés törlése?")},text={Text("Ez a korábbi statisztikákat is módosíthatja.")},confirmButton={TextButton(onClick={onSave(l.copy(snapshots=l.snapshots.filter { it.id!=delete!!.id }));delete=null}){Text("Törlés",color=red)}},dismissButton={TextButton(onClick={delete=null}){Text("Mégse")}})
    if(edit!=null) {
        val s=edit!!
        var note by remember(s.id) { mutableStateOf(s.note) }
        var editedDate by remember(s.id) { mutableLongStateOf(s.timestamp) }
        val editContext=LocalContext.current
        var values by remember(s.id) { mutableStateOf(s.entries.associate { it.accountId to it.value.toString() }) }
        AlertDialog(onDismissRequest={edit=null},title={Text("Mentés javítása")},text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState())) {
            TextButton(onClick={
                val c=Calendar.getInstance().apply { timeInMillis=editedDate }
                DatePickerDialog(editContext,{_,y,m,d->
                    editedDate=Calendar.getInstance().apply { timeInMillis=editedDate;set(Calendar.YEAR,y);set(Calendar.MONTH,m);set(Calendar.DAY_OF_MONTH,d) }.timeInMillis
                },c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).apply { datePicker.maxDate=System.currentTimeMillis() }.show()
            }) { Icon(Icons.Default.CalendarMonth,null); Spacer(Modifier.width(6.dp));Text("Dátum: ${date(editedDate)}") }
            s.entries.forEach { e ->
                val a=l.accounts.find { it.id==e.accountId }
                OutlinedTextField(values[e.accountId].orEmpty(),{v->if(v.isEmpty() || v.all(Char::isDigit)) values=values+(e.accountId to v)},label={Text(a?.name ?: "Archivált kategória")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)
            }
            OutlinedTextField(note,{note=it},label={Text("Megjegyzés")})
        }},confirmButton={TextButton(enabled=values.values.all { it.toLongOrNull()!=null },onClick={
            val updated=s.copy(timestamp=editedDate,note=note,entries=s.entries.map { it.copy(value=values[it.accountId]!!.toLong()) })
            onSave(l.copy(snapshots=l.snapshots.map { if(it.id==s.id) updated else it }));edit=null
        }){Text("Mentés")}},dismissButton={TextButton(onClick={edit=null}){Text("Mégse")}})
    }
}

@Composable private fun Settings(l:Ledger,password:String,onSave:(Ledger)->Unit,onPassword:(String)->Unit,onLock:()->Unit,onMessage:(String)->Unit) {
    val ctx=LocalContext.current
    var addPerson by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<Person?>(null) }
    var archive by remember { mutableStateOf<Account?>(null) }
    var changePw by remember { mutableStateOf(false) }
    var oldPw by remember { mutableStateOf("") };var newPw by remember { mutableStateOf("") };var again by remember { mutableStateOf("") }
    var importPw by remember { mutableStateOf("") }
    var imported by remember { mutableStateOf<ByteArray?>(null) }
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if(uri!=null) try { ctx.contentResolver.openOutputStream(uri)?.use { it.write(Vault.encryptedBytes(ctx)) };onMessage("Titkosított mentés exportálva") } catch(e:Exception) { onMessage("Exportálási hiba: ${e.message}") }
    }
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) try { imported=ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } } catch(e:Exception) { onMessage("Beolvasási hiba: ${e.message}") }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=18.dp)) {
        Heading("Beállítások","Személyek, kategóriák és adatvédelem")
        Text("Személyek",fontSize=18.sp,fontWeight=FontWeight.SemiBold)
        l.people.forEach { p -> Row(Modifier.fillMaxWidth().padding(vertical=5.dp).background(card,RoundedCornerShape(12.dp)).padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Default.Person,null,tint=accent);Spacer(Modifier.width(12.dp));Text(p.name,Modifier.weight(1f));IconButton(onClick={rename=p}){Icon(Icons.Default.Edit,null)}
        } }
        TextButton(onClick={addPerson=true}) { Icon(Icons.Default.PersonAdd,null);Text("Új személy") }
        Spacer(Modifier.height(12.dp))
        Text("Archivált kategóriák",fontSize=18.sp,fontWeight=FontWeight.SemiBold)
        l.accounts.filter { !it.active }.forEach { a -> Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text((l.people.find { it.id==a.personId }?.name ?: "?")+": "+a.name,Modifier.weight(1f))
            TextButton(onClick={onSave(l.copy(accounts=l.accounts.map { if(it.id==a.id) it.copy(active=true) else it }))}) { Text("Visszaállítás") }
        } }
        Spacer(Modifier.height(14.dp))
        Text("Biztonság",fontSize=18.sp,fontWeight=FontWeight.SemiBold)
        Button(onClick={changePw=true},modifier=Modifier.fillMaxWidth()) { Icon(Icons.Default.Key,null);Spacer(Modifier.width(8.dp));Text("Saját jelszó módosítása") }
        OutlinedButton(onClick=onLock,modifier=Modifier.fillMaxWidth()) { Icon(Icons.Default.Lock,null);Spacer(Modifier.width(8.dp));Text("Zárolás most") }
        Spacer(Modifier.height(14.dp))
        Text("Biztonsági mentés",fontSize=18.sp,fontWeight=FontWeight.SemiBold)
        Text("Az exportált fájl AES-256-GCM titkosítású. A visszaállításhoz az exportáláskor használt jelszó kell.",fontSize=12.sp,color=muted)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick={exporter.launch("vagyon-naplo-mentes.vault")},modifier=Modifier.fillMaxWidth()) { Icon(Icons.Default.Upload,null);Spacer(Modifier.width(8.dp));Text("Titkosított mentés exportálása") }
        OutlinedButton(onClick={importer.launch(arrayOf("*/*"))},modifier=Modifier.fillMaxWidth()) { Icon(Icons.Default.Download,null);Spacer(Modifier.width(8.dp));Text("Mentés visszaállítása") }
        Spacer(Modifier.height(20.dp))
        Text("A jelszó nem azonos a telefon feloldókódjával. A pénzügyi adatok titkosítva, kizárólag ezen a készüléken tárolódnak.",fontSize=12.sp,color=muted)
        Spacer(Modifier.height(30.dp))
    }
    if(addPerson || rename!=null) {
        var name by remember(addPerson,rename?.id) { mutableStateOf(rename?.name.orEmpty()) }
        AlertDialog(onDismissRequest={addPerson=false;rename=null},title={Text(if(addPerson) "Új személy" else "Személy átnevezése")},text={OutlinedTextField(name,{name=it},label={Text("Név")},singleLine=true)},confirmButton={TextButton(enabled=name.isNotBlank(),onClick={
            onSave(if(addPerson) l.copy(people=l.people+Person(id(),name.trim())) else l.copy(people=l.people.map { if(it.id==rename!!.id) it.copy(name=name.trim()) else it }));addPerson=false;rename=null
        }){Text("Mentés")}},dismissButton={TextButton(onClick={addPerson=false;rename=null}){Text("Mégse")}})
    }
    if(changePw) AlertDialog(onDismissRequest={changePw=false},title={Text("Jelszó módosítása")},text={Column {
        OutlinedTextField(oldPw,{oldPw=it},label={Text("Jelenlegi jelszó")},visualTransformation=PasswordVisualTransformation())
        OutlinedTextField(newPw,{newPw=it},label={Text("Új jelszó (min. 8 karakter)")},visualTransformation=PasswordVisualTransformation())
        OutlinedTextField(again,{again=it},label={Text("Új jelszó ismét")},visualTransformation=PasswordVisualTransformation())
    }},confirmButton={TextButton(enabled=newPw.length>=8 && newPw==again,onClick={
        if(oldPw==password) { try { Vault.save(ctx,l,newPw);onPassword(newPw);changePw=false;oldPw="";newPw="";again="";onMessage("Jelszó módosítva") } catch(e:Exception){onMessage("Hiba: ${e.message}")} }
        else onMessage("Hibás jelenlegi jelszó")
    }){Text("Módosítás")}},dismissButton={TextButton(onClick={changePw=false}){Text("Mégse")}})
    if(imported!=null) AlertDialog(onDismissRequest={imported=null;importPw=""},title={Text("Mentés visszaállítása")},text={Column {
        Text("FIGYELEM: a jelenlegi adatok helyére a mentés adatai kerülnek. Add meg a mentés készítésekor használt jelszót.")
        OutlinedTextField(importPw,{importPw=it},label={Text("Mentés jelszava")},visualTransformation=PasswordVisualTransformation())
    }},confirmButton={TextButton(onClick={
        try { Vault.restore(ctx,imported!!,importPw);imported=null;importPw="";onLock() }
        catch(e:Exception) { onMessage("Nem sikerült visszaállítani. Ellenőrizd a jelszót és a fájlt.") }
    }){Text("Visszaállítás")}},dismissButton={TextButton(onClick={imported=null;importPw=""}){Text("Mégse")}})
}
