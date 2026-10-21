package io.shamash.feature.companion

/**
 * The phone page's markup and style. `{{key}}` are the page's words (HTML-escaped), `{{script}}` its one script;
 * values typed into fields are never written into the page. No inline handlers: the CSP lets only the nonce script
 * run. SPEC: CMP-02, CMP-06..16
 */
internal object PageMarkup {
    private val STYLE = """
:root{color-scheme:dark}*{box-sizing:border-box}
body{margin:0;min-height:100vh;display:flex;justify-content:center;padding:24px 16px;
font-family:system-ui,-apple-system,"Segoe UI",Roboto,"Noto Sans Arabic",sans-serif;color:#F5F0E6;
background:radial-gradient(120% 70% at 50% -10%,rgba(244,196,80,.26),rgba(244,196,80,0) 60%),#0A0C1E}
main{width:100%;max-width:520px}
.sun{width:48px;height:48px;border-radius:50%;margin:0 auto 16px;
background:radial-gradient(circle at 35% 35%,#FFE28C,#F4C450 60%,#C9962B);box-shadow:0 0 48px rgba(244,196,80,.55)}
h1{font-size:26px;margin:0 0 8px;text-align:center;letter-spacing:-.01em}
h2{font-size:17px;margin:20px 0 8px}
.intro,.note{margin:0 0 16px;color:rgba(245,240,230,.7);line-height:1.5}.intro{text-align:center}
.who{text-align:center;margin:0 0 16px;color:rgba(245,240,230,.8)}
.card{background:#14172E;border:1px solid rgba(245,240,230,.08);border-radius:24px;padding:20px;margin-bottom:16px;
box-shadow:0 24px 64px rgba(0,0,0,.45)}
label{display:block;font-size:13px;color:rgba(245,240,230,.6);margin:12px 0 8px}
input,textarea,select{width:100%;font:inherit;font-size:17px;color:#F5F0E6;background:#0A0C1E;
border:1.5px solid rgba(245,240,230,.12);border-radius:14px;padding:12px 14px;outline:none}
input:focus,textarea:focus,select:focus{border-color:#F4C450}
#pin,#ppin{letter-spacing:.5em;text-align:center;font-size:24px;font-variant-numeric:tabular-nums;direction:ltr}
textarea{height:120px;resize:none;line-height:1.4}
.send{margin-top:16px;width:100%;border:0;border-radius:14px;padding:14px;font:inherit;font-size:17px;font-weight:700;
color:#0A0C1E;background:linear-gradient(180deg,#FFE28C,#F4C450);cursor:pointer}
.send:disabled{opacity:.6}.alt{background:#262A4A;color:#F5F0E6}
.two{display:grid;grid-template-columns:1fr 1fr;gap:10px}
.tabs{display:flex;gap:8px;overflow-x:auto;padding:0 0 12px;margin-bottom:4px}
.tab{flex:none;border:1px solid rgba(245,240,230,.15);background:none;color:#F5F0E6;border-radius:999px;
padding:8px 14px;font:inherit;font-size:15px;cursor:pointer}.tab.on{background:#F4C450;color:#0A0C1E;border-color:#F4C450}
.rows{list-style:none;margin:0;padding:0}.row{padding:10px 0;border-bottom:1px solid rgba(245,240,230,.08)}
.row.off .name{opacity:.5}.bar{display:flex;flex-wrap:wrap;gap:6px;margin-top:8px}
.mini,.pick{border:1px solid rgba(245,240,230,.15);background:#0A0C1E;color:#F5F0E6;border-radius:10px;padding:8px 12px;
font:inherit;font-size:14px;cursor:pointer}.pick{display:block;width:100%;margin:6px 0;text-align:start}
.check{display:flex;align-items:center;gap:10px;color:#F5F0E6;font-size:15px}.check input{width:auto}
.fname{display:inline-block;margin-inline-end:8px}.tried{margin:12px 0 0;padding-inline-start:18px;color:rgba(245,240,230,.8)}
#msg{min-height:24px;margin:8px 0 16px;text-align:center;font-weight:600}
.ok{color:#7DDB9B}.bad{color:#FF6B5E}.wait{color:rgba(245,240,230,.6)}
.link{display:block;margin:8px auto 0;background:none;border:0;color:rgba(245,240,230,.5);font:inherit;font-size:14px;
text-decoration:underline;cursor:pointer}
[hidden]{display:none!important}
""".trimIndent()

    private val BODY = """
<div class="sun" aria-hidden="true"></div><h1>{{title}}</h1><p class="intro">{{intro}}</p>
<p id="who" class="who" hidden>{{profile}}: <b id="whoname"></b></p>
<div id="pinbox" class="card" hidden><label for="pin">{{pin}}</label>
<input id="pin" inputmode="numeric" maxlength="{{digits}}" autocomplete="off">
<button id="pingo" class="send" type="button">{{go}}</button></div>
<div id="ppinbox" class="card" hidden><label id="ppinlabel" for="ppin"></label>
<input id="ppin" type="password" inputmode="numeric" maxlength="4" autocomplete="off">
<button id="ppingo" class="send" type="button">{{go}}</button></div>
<nav class="tabs" role="tablist">
<button class="tab on" type="button" data-tab="send">{{tab_send}}</button>
<button class="tab" type="button" data-tab="type">{{tab_type}}</button>
<button class="tab" type="button" data-tab="rows">{{tab_rows}}</button>
<button class="tab" type="button" data-tab="teams">{{tab_teams}}</button>
<button class="tab" type="button" data-tab="feeds">{{tab_feeds}}</button>
<button class="tab" type="button" data-tab="backup">{{tab_backup}}</button>
</nav>
<section id="tab-send" class="card"><label for="text">{{text}}</label><textarea id="text" placeholder="{{text}}"></textarea>
<div class="two"><button id="go" class="send" type="button">{{open_now}}</button>
<button id="queue" class="send alt" type="button">{{add_queue}}</button></div></section>
<section id="tab-type" class="card" hidden><p class="note">{{type_intro}}</p><div id="fields"></div>
<p id="nofields" class="note" hidden>{{type_none}}</p>
<div id="fillbox" hidden><label id="filllabel" for="fill"></label><input id="fill" autocomplete="off">
<button id="fillgo" class="send" type="button">{{type_send}}</button></div>
<button id="fieldsrefresh" class="link" type="button">{{refresh}}</button></section>
<section id="tab-rows" class="card" hidden><p class="note">{{rows_intro}}</p><ol id="rows" class="rows"></ol>
<button id="rowsave" class="send" type="button">{{save}}</button></section>
<section id="tab-teams" class="card" hidden><label for="teams">{{teams_intro}}</label><textarea id="teams"></textarea>
<h2>{{competitions}}</h2><div id="comps"></div><button id="teamsave" class="send" type="button">{{save}}</button></section>
<section id="tab-feeds" class="card" hidden><p class="note">{{feeds_intro}}</p><ul id="feeds" class="rows"></ul>
<label for="fname">{{feed_name}}</label><input id="fname" maxlength="60" autocomplete="off">
<label for="furl">{{feed_url}}</label><input id="furl" inputmode="url" autocomplete="off" dir="ltr">
<label for="fkind">{{feed_kind}}</label><select id="fkind"><option value="RSS">RSS</option>
<option value="ATOM">Atom</option><option value="JSON">JSON</option></select>
<label for="fstyle">{{feed_style}}</label><select id="fstyle"><option value="WIDE">{{style_wide}}</option>
<option value="VIDEO">{{style_video}}</option><option value="POSTER">{{style_poster}}</option></select>
<label for="flevel">{{feed_level}}</label><select id="flevel"><option value="ALL">{{level_all}}</option>
<option value="FAMILY">{{level_family}}</option><option value="KIDS">{{level_kids}}</option></select>
<label class="check"><input id="fhttp" type="checkbox"> {{feed_http}}</label>
<div class="two"><button id="ftry" class="send alt" type="button">{{feed_try}}</button>
<button id="fadd" class="send" type="button">{{feed_add}}</button></div><ul id="tried" class="tried"></ul></section>
<section id="tab-backup" class="card" hidden><p class="note">{{backup_intro}}</p><h2>{{export}}</h2>
<label for="xp1">{{passphrase}}</label><input id="xp1" type="password" autocomplete="new-password">
<label for="xp2">{{passphrase_again}}</label><input id="xp2" type="password" autocomplete="new-password">
<label class="check"><input id="xkeys" type="checkbox"> {{include_keys}}</label>
<label class="check"><input id="xpins" type="checkbox"> {{include_pins}}</label>
<button id="xgo" class="send" type="button">{{export}}</button><h2>{{import}}</h2>
<label for="ifile">{{file}}</label><input id="ifile" type="file" accept=".shamash,application/octet-stream,application/json">
<label for="ip">{{passphrase}}</label><input id="ip" type="password" autocomplete="off">
<button id="igo" class="send" type="button">{{import}}</button></section>
<div id="msg" role="status" aria-live="polite"></div>
<button id="change" class="link" type="button" hidden>{{change}}</button>
""".trimIndent()

    val HTML = "<!doctype html><html lang=\"{{lang}}\" dir=\"{{dir}}\"><head><meta charset=\"utf-8\">\n" +
        "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,viewport-fit=cover\">\n" +
        "<meta name=\"theme-color\" content=\"#0A0C1E\"><title>{{title}}</title>\n<style>\n" + STYLE +
        "\n</style></head><body><main>\n" + BODY + "\n</main><script nonce=\"{{nonce}}\">\n{{script}}\n</script></body></html>"
}
