import {createServer} from 'node:http';
import {readFile} from 'node:fs/promises';
import {join} from 'node:path';
const fixtures = process.argv[2];
let redirectHits = 0, uploadSource = '', requestKey = '', selfieBody = '', selfieUpload = '', selfieKey = '';
let challengeRequests = 0, facialBody = '', locationBody = '';
const biometrics = [], submits = [], attempts = [], codes = [], dropped = new Set(), answered = new Set();
const trap = createServer((req,res) => { redirectHits++; res.end('{}'); }).listen(0,'127.0.0.1');
const field = (body,name) => body.match(new RegExp(`name="${name}"\\r\\n\\r\\n([^\\r]*)`))?.[1] ?? null;
const server = createServer(async (req,res) => {
 const token = req.headers.authorization;
 if (req.url === '/stats') {res.setHeader('Content-Type','application/json');return res.end(JSON.stringify({redirectHits,uploadSource,requestKey,selfieBody,selfieUpload,selfieKey,challengeRequests,biometrics,submits,attempts,codes,facialBody,locationBody}));}
 if (token === 'Bearer redirect') {res.writeHead(302,{Location:`http://127.0.0.1:${trap.address().port}/`});return res.end();}
 if (token === 'Bearer slow') {return setTimeout(() => {res.writeHead(200);res.end('{}');},2000);}
 if (token === 'Bearer broken-body') {res.writeHead(200,{'Content-Type':'application/json','Content-Length':'100000'});res.flushHeaders();res.write('{"success":');return setTimeout(()=>res.destroy(),40);}
 if (token === 'Bearer bad-json') {res.writeHead(200);return res.end('not-json');}
 const chunks=[];for await(const chunk of req)chunks.push(chunk);
 const raw=Buffer.concat(chunks);
 const body=raw.toString('latin1');
 const key = req.headers['x-idempotency-key'];
 if (token === 'Bearer flaky' && key) {
  attempts.push({path:req.url.replace(/^.*\/sessions\/[^/]+/,''),key,type:req.url.endsWith('/submit')?JSON.parse(raw.toString('utf8')).type:field(body,'type')});
  if (!dropped.has(key)) {dropped.add(key);return req.socket.destroy();}
 }
 const send = async (status,fixture) => {res.writeHead(status,{'Content-Type':'application/json'});res.end(await readFile(join(fixtures,fixture+'.json')));};
 if (token === 'Bearer unauthorized') return send(401,'unauthorized');
 const rejection = token?.match(/^Bearer reject:([a-z_.]+)$/)?.[1];
 if (token === 'Bearer closed-submit' && (req.method === 'POST' || answered.has(token))) {answered.add(token);return send(409,'session-closed');}
 if (token === 'Bearer closed-offline' && req.method === 'POST') {answered.add(token);return send(409,'session-closed');}
 if (token === 'Bearer closed-offline' && answered.has(token)) return req.socket.destroy();
 if (rejection && (req.url.endsWith('/submit') || /-codes(\/confirm)?$/.test(req.url))) return send(422,'rejections/'+rejection);
 if (token === 'Bearer resend-with-time' && /-codes$/.test(req.url)) return send(422,'rejections/phone.resend_too_soon');
 if (token === 'Bearer closes' && req.method === 'GET' && answered.has(token)) return send(409,'session-closed');
 if (token === 'Bearer refresh-fails' && req.method === 'GET' && answered.has(token)) return req.socket.destroy();
 const code = req.url.match(/\/(email|phone)-codes(\/confirm)?$/);
 if (code) {
  codes.push({channel:code[1],confirm:Boolean(code[2]),body:JSON.parse(raw.toString('utf8')),key:req.headers['x-idempotency-key'] ?? null});
  if (code[2]) {answered.add(token);return code[1]==='email'?send(201,'email-code-confirm'):send(404,'unauthorized');}
  return send(202,code[1]+'-code-request');
 }
 if (req.method==='DELETE') return send(200,'cancelled');
 if (req.url.endsWith('/liveness-challenges')) {challengeRequests++;return send(201,'liveness-challenge');}
 if (req.url.endsWith('/uploads/documents')) {uploadSource=body.includes('name="capture_source"\r\n\r\nupload')?'upload':'unknown';requestKey=req.headers['x-idempotency-key'];return send(201,'upload');}
 if (req.url.endsWith('/uploads/biometrics')) {
  const type=field(body,'type');
  biometrics.push({type,challenge_id:field(body,'challenge_id'),gesture:field(body,'gesture'),capture_source:field(body,'capture_source'),content_type:body.match(/name="file"; filename="[^"]*"\r\nContent-Type: ([^\r]*)/)?.[1] ?? null,filename:body.match(/name="file"; filename="([^"]*)"/)?.[1] ?? null,key:req.headers['x-idempotency-key'] ?? null,fields:[...body.matchAll(/name="([^"]+)"/g)].map(m=>m[1])});
  selfieUpload=body;selfieKey=req.headers['x-idempotency-key'];
  return send(201,type==='gesture'?'gesture-upload':type==='liveness_video'?'video-upload':type==='selfie'?'selfie-upload':'facial-upload');
 }
 if (req.url.endsWith('/submit')) {
  const text=raw.toString('utf8');
  submits.push({type:(()=>{try{return JSON.parse(text).type}catch{return null}})(),key:req.headers['x-idempotency-key'] ?? null});
  if (text.includes('"location_verification"')) {locationBody=text;return token==='Bearer reject-location'?send(422,'location-rejection'):token==='Bearer reject-place'?send(422,'reference-rejection'):send(201,'location-submission');}
  if (text.includes('"facial_verification"')) {selfieBody=text;facialBody=text;return send(201,'facial-submission');}
  if (text.includes('"form_submission"')) {answered.add(token);submits[submits.length-1].fields=JSON.parse(text).fields;return JSON.parse(text).fields.employer !== undefined && JSON.parse(text).fields.employment !== 'Employed' ? send(422,'questionnaire-hidden-answer') : send(201,'questionnaire-submission');}
  return send(201,'submission');
 }
 if (token==='Bearer earlier-answers') return send(200,answered.has(token)?'questionnaire-earlier-answers-session':'questionnaire-session');
 if (token==='Bearer reasked-optional' && !answered.has(token)) {
  const session=JSON.parse(await readFile(join(fixtures,'questionnaire-session.json'),'utf8'));
  for (const question of session.data.profile.form_schema) if (['employment','birth_date'].includes(question.key)) question.required=false;
  session.data.profile.form_answers={country:'DE',employment:'Student',birth_date:'1990-05-17',terms:true};
  res.writeHead(200,{'Content-Type':'application/json'});return res.end(JSON.stringify(session));
 }
 if (token==='Bearer questionnaire' || token==='Bearer closed-submit' || token==='Bearer closed-offline' || token==='Bearer refresh-fails' || token==='Bearer closes' || token==='Bearer reasked-optional') return send(200,answered.has(token)?'requirement-added-session':'questionnaire-session');
 return send(200,token==='Bearer selfie'?'facial-session':token==='Bearer capture'?'capture-session':'capture-snapshot-or-upload');
});
server.listen(Number(process.env.PORT || 0),'127.0.0.1',()=>console.log(server.address().port));
