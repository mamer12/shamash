package io.shamash.feature.companion

import android.content.res.Resources
import io.shamash.R

/**
 * The phone's page: a 6-digit PIN asked once (then kept in the phone's localStorage), a box, a Send button and the TV's answer.
 * `?text=` / `?url=` fill the box, for the phone's share target. Words come from the TV's string resources, so the
 * page speaks the TV's language. SPEC: CMP-02
 */
internal object CompanionPage {
    fun render(res: Resources, nonce: String): String = fill(
        mapOf(
            "lang" to res.getString(R.string.companion_page_lang),
            "dir" to res.getString(R.string.companion_page_dir),
            "title" to res.getString(R.string.companion_page_title),
            "intro" to res.getString(R.string.companion_page_intro),
            "pin" to res.getString(R.string.companion_page_pin),
            "text" to res.getString(R.string.companion_page_text),
            "send" to res.getString(R.string.companion_page_send),
            "sending" to res.getString(R.string.companion_page_sending),
            "offline" to res.getString(R.string.companion_page_offline),
            "wrong" to res.getString(R.string.companion_wrong_pin),
            "change" to res.getString(R.string.companion_page_change_pin),
        ),
        nonce,
    )

    /**
     * Puts [texts] into the template: HTML-escaped in the markup, as a JS object for the script. [nonce] marks the one
     * script the page's Content-Security-Policy lets run.
     */
    fun fill(texts: Map<String, String>, nonce: String = ""): String {
        val messages = texts.entries.joinToString(",", "{", "}") { (k, v) -> "\"$k\":\"${jsEscape(v)}\"" }
        return texts.entries.fold(TEMPLATE) { page, (k, v) -> page.replace("{{$k}}", htmlEscape(v)) }
            .replace("{{nonce}}", htmlEscape(nonce))
            .replace("{{messages}}", messages)
            .replace("{{digits}}", Pins.DIGITS.toString())
    }

    private fun htmlEscape(s: String) =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

    private fun jsEscape(s: String) = buildString {
        s.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '<' -> append("\\u003c")
                '>' -> append("\\u003e")
                '&' -> append("\\u0026")
                else -> append(c)
            }
        }
    }

    private val TEMPLATE = """
<!doctype html><html lang="{{lang}}" dir="{{dir}}"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="theme-color" content="#0A0C1E"><title>{{title}}</title>
<style>
:root{color-scheme:dark}*{box-sizing:border-box}
body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px 16px;
font-family:system-ui,-apple-system,"Segoe UI",Roboto,"Noto Sans Arabic",sans-serif;color:#F5F0E6;
background:radial-gradient(120% 70% at 50% -10%,rgba(244,196,80,.26),rgba(244,196,80,0) 60%),#0A0C1E}
main{width:100%;max-width:480px}
.sun{width:56px;height:56px;border-radius:50%;margin:0 auto 20px;
background:radial-gradient(circle at 35% 35%,#FFE28C,#F4C450 60%,#C9962B);box-shadow:0 0 48px rgba(244,196,80,.55)}
h1{font-size:28px;margin:0 0 8px;text-align:center;letter-spacing:-.01em}
.intro{margin:0 0 24px;text-align:center;color:rgba(245,240,230,.7);line-height:1.5}
.card{background:#14172E;border:1px solid rgba(245,240,230,.08);border-radius:24px;padding:20px;
box-shadow:0 24px 64px rgba(0,0,0,.45)}
label{display:block;font-size:13px;color:rgba(245,240,230,.6);margin:0 0 8px}
input,textarea{width:100%;font:inherit;font-size:18px;color:#F5F0E6;background:#0A0C1E;
border:1.5px solid rgba(245,240,230,.12);border-radius:16px;padding:14px 16px;outline:none;transition:border-color .15s}
input:focus,textarea:focus{border-color:#F4C450}
#pin{letter-spacing:.5em;text-align:center;font-size:24px;font-variant-numeric:tabular-nums;direction:ltr}
#pinbox{margin-bottom:16px}textarea{height:132px;resize:none;line-height:1.4}
.send{margin-top:16px;width:100%;border:0;border-radius:16px;padding:16px;font:inherit;font-size:18px;font-weight:700;
color:#0A0C1E;background:linear-gradient(180deg,#FFE28C,#F4C450);cursor:pointer;transition:transform .1s,opacity .15s}
.send:active{transform:scale(.98)}.send:disabled{opacity:.6}
#msg{min-height:24px;margin-top:16px;text-align:center;font-weight:600}
.ok{color:#7DDB9B}.bad{color:#FF6B5E}.wait{color:rgba(245,240,230,.6)}
.link{display:block;margin:16px auto 0;background:none;border:0;color:rgba(245,240,230,.5);font:inherit;font-size:14px;
text-decoration:underline;cursor:pointer}
[hidden]{display:none!important}
</style></head><body><main>
<div class="sun" aria-hidden="true"></div><h1>{{title}}</h1><p class="intro">{{intro}}</p>
<div class="card">
<div id="pinbox" hidden><label for="pin">{{pin}}</label>
<input id="pin" inputmode="numeric" pattern="[0-9]*" maxlength="{{digits}}" autocomplete="off"></div>
<label for="text">{{text}}</label><textarea id="text" placeholder="{{text}}"></textarea>
<button id="go" class="send" type="button">{{send}}</button><div id="msg" role="status" aria-live="polite"></div>
</div><button id="change" class="link" type="button" hidden>{{change}}</button>
</main><script nonce="{{nonce}}">
(function(){
var T={{messages}},KEY='shamash-pin';
function el(id){return document.getElementById(id)}
function load(){try{return localStorage.getItem(KEY)||''}catch(e){return ''}}
function keep(v){try{if(v)localStorage.setItem(KEY,v);else localStorage.removeItem(KEY)}catch(e){}}
function say(text,kind){var m=el('msg');m.textContent=text;m.className=kind}
function askPin(show){el('pinbox').hidden=!show;el('change').hidden=show;if(show)el('pin').focus()}
var pin=load();askPin(!pin);
var q=new URLSearchParams(location.search);
var shared=[q.get('text'),q.get('url')].filter(Boolean).join(' ');
if(shared)el('text').value=shared;
el('change').onclick=function(){pin='';keep('');el('pin').value='';askPin(true)};
el('go').onclick=function(){
var p=pin||el('pin').value.trim(),t=el('text').value.trim();
if(!/^[0-9]{{{digits}}}${'$'}/.test(p)){askPin(true);say(T.pin,'bad');return}
if(!t){el('text').focus();return}
el('go').disabled=true;say(T.sending,'wait');
fetch('/send',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded','X-Shamash':'1'},
body:'pin='+encodeURIComponent(p)+'&text='+encodeURIComponent(t)})
.then(function(r){return r.text().then(function(x){
if(r.status===403){pin='';keep('');el('pin').value='';askPin(true);say(T.wrong,'bad');return}
if(!r.ok){say(x||T.offline,'bad');return}
pin=p;keep(p);askPin(false);say(x,'ok');el('text').value=''})})
.catch(function(){say(T.offline,'bad')})
.then(function(){el('go').disabled=false});
};
})();
</script></body></html>
""".trimIndent()
}
