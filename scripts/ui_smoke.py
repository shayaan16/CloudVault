"""Front-end-only smoke tests. Uses a fixture HTTP server, NOT the Spring Boot API.
Run: pip install playwright; playwright install chromium; python scripts/ui_smoke.py
The preview screenshot is generated from fixtures and is not an end-to-end result.
"""
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from pathlib import Path
from threading import Thread
import json, os, urllib.parse
from playwright.sync_api import sync_playwright
ROOT=Path(__file__).resolve().parents[1]
STATIC=ROOT/'src/main/resources/static'
FOLDERS=[dict(id=i+1,parentId=None,name=n,quickAccess=i>=3,openedAt=None,fileCount=1 if i==0 else 0) for i,n in enumerate(['Documents','Images','Audio','Project materials','Study documents'])]
FILES=[dict(id=1,folderId=1,name='Project notes.pdf',mimeType='application/pdf',sizeBytes=505856,favorite=True,uploadedAt='2026-11-25T12:00:00Z',deletedAt=None)]
STATS=dict(used=505856,limit=524288000,documents=505856,media=0,other=0,count=1,trash=0)
class Handler(SimpleHTTPRequestHandler):
    def __init__(self,*a,**kw):super().__init__(*a,directory=str(STATIC),**kw)
    def log_message(self,*a):pass
    def payload(self,obj,status=200):
        raw=json.dumps(obj).encode();self.send_response(status);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(raw)));self.end_headers();self.wfile.write(raw)
    def do_GET(self):
        path,_,query=self.path.partition('?');p=urllib.parse.parse_qs(query)
        if path=='/api/auth/me':return self.payload(dict(id=1,name='Workspace owner',email='owner@example.test',demo=False))
        if path=='/api/auth/csrf':return self.payload(dict(token='fixture',header='X-CSRF-TOKEN'))
        if path=='/api/folders':return self.payload(FOLDERS)
        if path=='/api/stats':return self.payload(STATS)
        if path=='/api/files':
            scope=p.get('scope',['all'])[0];files=FILES[:]
            if scope=='trash':files=[]
            if scope=='folder':files=[x for x in files if str(x['folderId'])==p.get('folderId',[''])[0]]
            if scope=='favorites':files=[x for x in files if x['favorite']]
            if 'q' in p:files=[x for x in files if p['q'][0].lower() in x['name'].lower()]
            return self.payload(files)
        if path.startswith('/api/shares/'):return self.payload([])
        if path.startswith('/api/public/'):return self.payload(dict(name='Project notes.pdf',sizeBytes=505856,mimeType='application/pdf'))
        if path=='/api/files/1/download':
            raw=b'fixture content';self.send_response(200);self.send_header('Content-Type','application/octet-stream');self.send_header('Content-Length',str(len(raw)));self.end_headers();self.wfile.write(raw);return
        if path.startswith('/webjars/'):self.send_response(200);self.send_header('Content-Type','text/css');self.end_headers();return
        return super().do_GET()
    def do_POST(self):
        raw=self.rfile.read(int(self.headers.get('Content-Length','0')))
        if self.path=='/api/folders':
            b=json.loads(raw);FOLDERS.append(dict(id=len(FOLDERS)+1,parentId=b['parentId'],name=b['name'],quickAccess=False,openedAt=None,fileCount=0))
        if self.path=='/api/files':
            FILES.append(dict(id=2,folderId=None,name='smoke.txt',mimeType='text/plain',sizeBytes=4,favorite=False,uploadedAt='2026-11-26T12:00:00Z',deletedAt=None))
        return self.payload(dict(message='Fixture response'))
    def do_PATCH(self):
        raw=self.rfile.read(int(self.headers.get('Content-Length','0')));b=json.loads(raw)
        if self.path=='/api/files/1':FILES[0].update(b)
        return self.payload(dict(message='Fixture response'))
server=ThreadingHTTPServer(('127.0.0.1',0),Handler);Thread(target=server.serve_forever,daemon=True).start()
base=f'http://127.0.0.1:{server.server_port}'
errors=[]
try:
    with sync_playwright() as p:
        executable=os.environ.get('CHROMIUM_PATH')
        browser=p.chromium.launch(headless=True,executable_path=executable,args=['--no-sandbox'])
        page=browser.new_page(viewport={'width':1653,'height':895},accept_downloads=True)
        page.on('pageerror',lambda e:errors.append(str(e)))
        page.goto(base+'/#cloud');page.get_by_role('heading',name='Recent uploads').wait_for()
        page.screenshot(path=str(ROOT/'docs'/'dashboard-ui-preview.png'))
        for title,selector in [('Home','home'),('Dashboard','dashboard'),('Folders','folders'),('Recent files','recent'),('Favorites','favorites'),('Shared','shared'),('Recycle Bin','trash'),('Settings','settings')]:
            page.locator(f'nav a[data-page="{selector}"]').click();page.wait_for_function('(title)=>document.querySelector("#page-title").textContent===title',arg=title)
        page.locator('nav a[data-page="cloud"]').click();page.get_by_role('heading',name='Recent uploads').wait_for()
        page.get_by_role('button',name='Open folder').first.click();page.get_by_role('heading',name='Documents',exact=True).wait_for()
        page.locator('#search').fill('notes');page.locator('#search').press('Enter');page.get_by_role('heading',name='Search results',exact=True).first.wait_for();page.get_by_text('Project notes.pdf',exact=True).wait_for()
        page.locator('nav a[data-page="cloud"]').click();page.get_by_role('button',name='+ New folder').click();page.get_by_label('Folder name').fill('Smoke folder');page.get_by_role('button',name='Create folder').click();page.get_by_text('Folder created.').wait_for()
        page.locator('nav a[data-page="folders"]').click();page.get_by_text('Smoke folder',exact=True).wait_for()
        page.locator('#file-input').set_input_files({'name':'smoke.txt','mimeType':'text/plain','buffer':b'test'});page.get_by_text('1 file(s) uploaded.').wait_for();page.get_by_text('smoke.txt',exact=True).wait_for()
        page.locator('nav a[data-page="cloud"]').click();page.get_by_text('Project notes.pdf',exact=True).click();page.get_by_role('button',name='Rename',exact=True).click();page.get_by_label('File name').fill('Updated notes.pdf');page.get_by_role('button',name='Save',exact=True).click();page.get_by_text('Updated notes.pdf',exact=True).wait_for()
        page.get_by_text('Updated notes.pdf',exact=True).click();page.get_by_role('button',name='Download',exact=True).click();
        mobile=browser.new_page(viewport={'width':390,'height':844},is_mobile=True,has_touch=True);mobile.on('pageerror',lambda e:errors.append(str(e)));mobile.goto(base+'/#cloud');mobile.get_by_role('heading',name='Recent uploads').wait_for();assert mobile.evaluate('document.documentElement.scrollWidth<=innerWidth');mobile.locator('#menu-toggle').click();mobile.locator('nav a[data-page="dashboard"]').click();mobile.get_by_role('heading',name='Workspace overview').wait_for()
        mobile.screenshot(path=str(ROOT/'docs'/'mobile-ui-preview.png'))
        page.goto(base+'/auth.html#register');page.get_by_role('heading',name='Create your account').wait_for();page.get_by_label('Full name').fill('Test user');page.get_by_label('Email address').fill('test@example.test');page.get_by_label('Password').fill('Password!1234');page.get_by_role('button',name='Create account').click();page.get_by_text('Fixture response').wait_for()
        page.goto(base+'/auth.html#reset='+'a'*43);page.get_by_role('heading',name='Set a new password').wait_for()
        page.goto(base+'/share.html#'+'a'*43);page.get_by_role('link',name='Download file').wait_for()
        browser.close()
    assert not errors,errors
    print('PASS: desktop navigation, folder, search, create, upload request, rename request, download click, mobile width/navigation, registration/reset forms and public share page (fixture API).')
finally:server.shutdown()
