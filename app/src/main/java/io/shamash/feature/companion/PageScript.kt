package io.shamash.feature.companion

/**
 * The phone page's one script. Every call carries `X-Shamash: 1` and the companion PIN in a header; a profile or
 * owner PIN is asked when the TV says so and kept only in memory; secrets (field values, passphrases) go in POST
 * bodies and are cleared once sent; text from the TV is set with textContent, never as HTML. SPEC: CMP-02, CMP-06..16
 */
internal object PageScript {
    val JS = """
(function(){
var T={{messages}},KEY='shamash-pin',DIGITS={{digits}},TABS=['send','type','rows','teams','feeds','backup'];
var pin=load(),ppin='',retryAfterPin=null,rows=[],teamsExtra=[],picked=null;
function el(id){return document.getElementById(id)}
function load(){try{return localStorage.getItem(KEY)||''}catch(e){return ''}}
function keep(v){try{if(v)localStorage.setItem(KEY,v);else localStorage.removeItem(KEY)}catch(e){}}
function say(text,kind){var m=el('msg');m.textContent=text||'';m.className=kind||''}
function word(code){return T[code]||T.failed}
function digits(s,n){if(s.length!==n)return false;for(var i=0;i<n;i++){var c=s.charAt(i);if(c<'0'||c>'9')return false}return true}
function node(tag,cls,text){var n=document.createElement(tag);if(cls)n.className=cls;if(text!=null)n.textContent=text;return n}
function button(text,fn,cls){var b=node('button',cls||'mini',text);b.type='button';b.addEventListener('click',fn);return b}
function tabButton(t){return document.querySelector('[data-tab="'+t+'"]')}
function askPin(show){el('pinbox').hidden=!show;el('change').hidden=show;if(show)el('pin').focus()}
function askProfilePin(who,retry,wrong){ppin='';retryAfterPin=retry;el('ppinlabel').textContent=who==='owner'?T.owner_pin:T.profile_pin;
el('ppin').value='';el('ppinbox').hidden=false;el('ppin').focus();say(wrong?T.profile_pin_wrong:'',wrong?'bad':'')}
function api(method,path,body,done,retry){
if(!pin){askPin(true);say(T.pin,'bad');done(null);return}
var h={'X-Shamash':'1','X-Shamash-Pin':pin},init={method:method,headers:h,cache:'no-store'};
if(ppin)h['X-Shamash-Profile-Pin']=ppin;
if(body!=null){h['Content-Type']='application/json';init.body=JSON.stringify(body)}
fetch(path,init).then(function(r){
var type=r.headers.get('Content-Type')||'';
if(type.indexOf('application/json')<0)return r.blob().then(function(b){done({status:r.status,blob:b,disposition:r.headers.get('Content-Disposition')||''})});
return r.json().then(function(j){handle(r.status,j,done,retry)})
}).catch(function(){say(T.offline,'bad');done(null)})}
function handle(status,j,done,retry){
var code=j&&j.code;
if(status===403&&code==='pin'){pin='';keep('');askPin(true);say(T.wrong,'bad');done(null);return}
if(status===429&&code==='locked'){say(T.locked,'bad');done(null);return}
if(status===401){askProfilePin(j.who,retry,code==='profile_pin_wrong');done(null);return}
if(status===423){ppin='';say(T.profile_locked,'bad');done(null);return}
if(code==='refused'){say(T.refused,'bad');done(null);return}
done({status:status,json:j})}
function answered(r,okWord){if(!r)return false;var j=r.json;say(j.ok?(okWord||word(j.code)):word(j.code),j.ok?'ok':'bad');return j.ok}
function show(tab){for(var i=0;i<TABS.length;i++){var t=TABS[i],b=tabButton(t);el('tab-'+t).hidden=t!==tab;b.className='tab'+(t===tab?' on':'');
b.setAttribute('aria-selected',t===tab?'true':'false')}say('');var load={type:loadFields,rows:loadRows,teams:loadTeams,feeds:loadFeeds}[tab];if(load)load()}
function loadState(){api('GET','/api/state',null,function(r){if(!r||r.status!==200)return;keep(pin);askPin(false);var s=r.json;
el('whoname').textContent=s.profile;el('who').hidden=false;var no=s.edit==='REFUSED';
tabButton('rows').hidden=no;tabButton('teams').hidden=no;tabButton('feeds').hidden=no;tabButton('backup').hidden=s.backup==='REFUSED'},loadState)}
function sendNow(){var p=pin||el('pin').value.trim(),t=el('text').value.trim();
if(!digits(p,DIGITS)){askPin(true);say(T.pin,'bad');return}if(!t){el('text').focus();return}
el('go').disabled=true;say(T.sending,'wait');
fetch('/send',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded','X-Shamash':'1'},
body:'pin='+encodeURIComponent(p)+'&text='+encodeURIComponent(t)})
.then(function(r){return r.text().then(function(x){
if(r.status===403){pin='';keep('');el('pin').value='';askPin(true);say(T.wrong,'bad');return}
if(!r.ok){say(x||T.offline,'bad');return}
pin=p;keep(p);askPin(false);say(x,'ok');el('text').value=''})})
.catch(function(){say(T.offline,'bad')}).then(function(){el('go').disabled=false})}
function addToQueue(){var t=el('text').value.trim();if(!t){el('text').focus();return}
api('POST','/api/queue',{text:t},function(r){if(answered(r,T.queued))el('text').value=''},addToQueue)}
function loadFields(){api('GET','/api/fields',null,function(r){if(!r)return;var box=el('fields'),list=r.json.fields||[];box.textContent='';
el('nofields').hidden=list.length>0;list.forEach(function(f){box.appendChild(button(f.code+' · '+f.label,function(){pick(f)},'pick'))})},loadFields)}
function pick(f){picked=f;var i=el('fill');el('filllabel').textContent=f.code+' · '+f.label;i.value='';i.type=f.secret?'password':'text';
i.setAttribute('autocomplete',f.secret?'new-password':'off');el('fillbox').hidden=false;i.focus()}
function sendField(){if(!picked)return;var i=el('fill'),value=i.value,code=picked.code;i.value='';
api('POST','/api/fields',{code:code,value:value},function(r){value='';if(answered(r,T.filled)){el('fillbox').hidden=true;picked=null;loadFields()}},null)}
function loadRows(){api('GET','/api/rows',null,function(r){if(!r)return;if(r.status!==200){say(word(r.json.code),'bad');return}rows=r.json.rows;drawRows()},loadRows)}
function drawRows(){var list=el('rows');list.textContent='';rows.forEach(function(row,i){
var li=node('li','row'+(row.hidden?' off':'')),name=node('input','name'),bar=node('div','bar');
name.value=row.title||'';name.placeholder=row.defaultTitle;name.maxLength=40;name.setAttribute('aria-label',T.rename);
name.addEventListener('input',function(){row.title=name.value});li.appendChild(name);
bar.appendChild(button(T.up,function(){move(i,-1)}));bar.appendChild(button(T.down,function(){move(i,1)}));
bar.appendChild(button(row.hidden?T.show:T.hide,function(){row.hidden=!row.hidden;drawRows()}));
bar.appendChild(button(row.pinned?T.unpin:T.pin_row,function(){row.pinned=!row.pinned;drawRows()}));
li.appendChild(bar);list.appendChild(li)})}
function move(i,d){var j=i+d;if(j<0||j>=rows.length)return;var t=rows[i];rows[i]=rows[j];rows[j]=t;drawRows()}
function saveRows(){var out=rows.map(function(r,i){return {id:r.id,title:(r.title||'').trim()||null,hidden:!!r.hidden,pinned:!!r.pinned,order:i}});
api('POST','/api/rows',{rows:out},function(r){answered(r,T.saved)},saveRows)}
function loadTeams(){api('GET','/api/teams',null,function(r){if(!r)return;if(r.status!==200){say(word(r.json.code),'bad');return}
var t=r.json,box=el('comps'),ids=t.catalog.map(function(c){return c.id});el('teams').value=t.teams.join('\n');box.textContent='';
teamsExtra=t.competitions.filter(function(id){return ids.indexOf(id)<0});
t.catalog.forEach(function(c){var l=node('label','check'),cb=node('input');cb.type='checkbox';cb.value=c.id;
cb.checked=t.competitions.indexOf(c.id)>=0;l.appendChild(cb);l.appendChild(document.createTextNode(' '+c.name));box.appendChild(l)})},loadTeams)}
function saveTeams(){var teams=el('teams').value.split('\n').map(function(s){return s.trim()}).filter(Boolean),comps=teamsExtra.slice(),
boxes=el('comps').getElementsByTagName('input');for(var i=0;i<boxes.length;i++)if(boxes[i].checked)comps.push(boxes[i].value);
api('POST','/api/teams',{teams:teams,competitions:comps},function(r){answered(r,T.saved)},saveTeams)}
function loadFeeds(){api('GET','/api/feeds',null,function(r){if(!r)return;if(r.status!==200){say(word(r.json.code),'bad');return}
var list=el('feeds');list.textContent='';r.json.feeds.forEach(function(f){var li=node('li','row');li.appendChild(node('span','fname',f.title+' · '+f.host));
li.appendChild(button(T.feed_remove,function(){feed({action:'remove',id:f.id})}));list.appendChild(li)})},loadFeeds)}
function feedForm(action){return {action:action,title:el('fname').value.trim(),url:el('furl').value.trim(),kind:el('fkind').value,
style:el('fstyle').value,level:el('flevel').value,allowHttp:el('fhttp').checked}}
function feed(body){api('POST','/api/feeds',body,function(r){if(!r)return;var j=r.json,tried=el('tried');tried.textContent='';
if(!j.ok){say(word(j.code),'bad');return}
if(j.code==='tried'){say('');(j.titles||[]).forEach(function(t){tried.appendChild(node('li',null,t))});return}
say(word(j.code),'ok');if(j.code==='added'){el('fname').value='';el('furl').value=''}loadFeeds()},function(){feed(body)})}
function fileName(d){var m=/filename="([^"]+)"/.exec(d);return m?m[1]:'shamash-backup.shamash'}
function save(blob,name){var a=document.createElement('a'),url=URL.createObjectURL(blob);a.href=url;a.download=name;
document.body.appendChild(a);a.click();setTimeout(function(){URL.revokeObjectURL(url);a.remove()},1000)}
function doExport(){var a=el('xp1').value,b=el('xp2').value;if(a.length<8){say(T.backup_weak_passphrase,'bad');return}
if(a!==b){say(T.mismatch,'bad');return}el('xgo').disabled=true;say(T.confirm_tv,'wait');
api('POST','/api/backup/export',{passphrase:a,includeKeys:el('xkeys').checked,includePins:el('xpins').checked},function(r){
el('xgo').disabled=false;if(!r)return;if(r.blob){if(r.status!==200){say(T.failed,'bad');return}
el('xp1').value='';el('xp2').value='';save(r.blob,fileName(r.disposition));say(T.exported,'ok');return}say(word(r.json.code),'bad')},doExport)}
function doImport(){var f=el('ifile').files&&el('ifile').files[0],p=el('ip').value;if(!f){el('ifile').focus();return}
if(f.size>1048576){say(T.backup_too_large,'bad');return}if(!p){el('ip').focus();return}
var reader=new FileReader();reader.onerror=function(){say(T.backup_not_a_backup,'bad')};reader.onload=function(){upload(String(reader.result))};reader.readAsText(f);
function upload(text){el('igo').disabled=true;say(T.confirm_tv,'wait');
api('POST','/api/backup/import',{passphrase:p,file:text},function(r){el('igo').disabled=false;if(!r)return;var j=r.json;
if(j.ok){el('ip').value='';p='';say(T.imported.replace('{a}',j.added).replace('{r}',j.replaced),'ok');loadState();return}
if(j.code==='wait'){say(T.wait.replace('{s}',j.seconds),'bad');return}say(word(j.code),'bad')},function(){upload(text)})}}
for(var i=0;i<TABS.length;i++)(function(t){tabButton(t).addEventListener('click',function(){show(t)})})(TABS[i]);
el('pingo').addEventListener('click',function(){var p=el('pin').value.trim();if(!digits(p,DIGITS)){say(T.pin,'bad');return}
pin=p;el('pin').value='';say('');loadState()});
el('ppingo').addEventListener('click',function(){var v=el('ppin').value.trim();el('ppin').value='';if(!digits(v,4)){say(T.profile_pin_wrong,'bad');return}
ppin=v;el('ppinbox').hidden=true;say('');var f=retryAfterPin;retryAfterPin=null;if(f)f()});
el('change').addEventListener('click',function(){pin='';ppin='';keep('');el('pin').value='';askPin(true)});
el('go').addEventListener('click',sendNow);el('queue').addEventListener('click',addToQueue);
el('fillgo').addEventListener('click',sendField);el('fieldsrefresh').addEventListener('click',loadFields);
el('rowsave').addEventListener('click',saveRows);el('teamsave').addEventListener('click',saveTeams);
el('ftry').addEventListener('click',function(){feed(feedForm('try'))});el('fadd').addEventListener('click',function(){feed(feedForm('add'))});
el('xgo').addEventListener('click',doExport);el('igo').addEventListener('click',doImport);
var q=new URLSearchParams(location.search),shared=[q.get('text'),q.get('url')].filter(Boolean).join(' ');
if(shared)el('text').value=shared;
if(pin)loadState();else askPin(true);
})();
""".trimIndent()
}
