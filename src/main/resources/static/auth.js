'use strict';
const form = $('#auth-form'), links = $('#auth-links'), message = $('#auth-message');
function field(label, name, type, autocomplete) {
  const wrap = el('label', label, 'field'), input = el('input'); input.name = name; input.type = type; input.required = true;
  if (autocomplete) input.autocomplete = autocomplete;
  if (type === 'password') input.maxLength = 72;
  wrap.append(input); form.append(wrap);
}
function link(label, target) { const a = el('a', label); a.href = target; links.append(a); }
function status(text, error = false) { message.textContent = text; message.hidden = false; message.classList.toggle('error', error); }
async function signIn(email,password) { await request('/api/auth/login',{method:'POST',body:new URLSearchParams({email,password})}); location.replace('/#cloud'); }
function render() {
  form.replaceChildren(); links.replaceChildren(); message.hidden=true;
  const raw = location.hash.slice(1), [mode, token] = raw.split('=');
  const active = ['register','forgot','resend','verify','reset'].includes(mode) ? mode : 'login';
  const copy = {login:['Welcome back','Sign in to your personal workspace.','Sign in'],register:['Create your account','Get 500 MB of personal storage.','Create account'],forgot:['Forgot password?','We will send a reset link to your email.','Send reset link'],resend:['Verify your email','Request a fresh verification email.','Send verification link'],verify:['Verify your email','Confirm your account to start using CloudVault.','Verify email'],reset:['Set a new password','Choose a new password with at least 10 characters.','Change password']}[active];
  $('#auth-title').textContent=copy[0]; $('#auth-subtitle').textContent=copy[1];
  if(active==='register') field('Full name','name','text','name');
  if(!['verify','reset'].includes(active)) field('Email address','email','email','email');
  if(['login','register','reset'].includes(active)) field('Password','password','password',active==='login'?'current-password':'new-password');
  const submit=el('button',copy[2],'button btn-dark'); submit.type='submit';form.append(submit);
  if(active==='login') {
    const demo=button('Explore read-only demo',async()=>{demo.disabled=true;try{await signIn('demo@cloudvault.example','CloudVaultDemo!2026');}catch(e){status(e.message,true);}finally{demo.disabled=false;}},'btn-light auth-demo');form.append(demo);
    link('Create an account','#register');link('Forgot password?','#forgot');link('Resend verification email','#resend');
  } else link('Back to sign in','#login');
  form.onsubmit=async event=>{event.preventDefault();submit.disabled=true;const data=Object.fromEntries(new FormData(form));try{
    if(active==='login') await signIn(data.email,data.password);
    else { if(['verify','reset'].includes(active))data.token=token;const response=await request('/api/auth/'+active,{method:'POST',body:data});status(response.message);if(['register','reset','verify'].includes(active))form.reset(); }
  }catch(e){status(e.message,true);}finally{submit.disabled=false;}};
}
window.addEventListener('hashchange',render);render();
