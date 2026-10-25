import test from 'node:test';
import assert from 'node:assert/strict';
import { resolveApiBaseUrl } from '../src/utils/apiBase.js';
test('local development keeps the Gateway default',()=>assert.equal(resolveApiBaseUrl(undefined,false),'http://localhost:8080/api'));
test('production requires an explicit Gateway',()=>assert.throws(()=>resolveApiBaseUrl(undefined,true),/VITE_API_BASE_URL/));
test('production rejects insecure and loopback Gateways',()=>{for(const value of ['http://gateway.example/api','https://localhost/api','https://shop.localhost/api','https://0.0.0.0/api','https://127.0.0.1/api','https://[::1]/api'])assert.throws(()=>resolveApiBaseUrl(value,true));});
test('production accepts and normalizes the HTTPS Gateway API path',()=>assert.equal(resolveApiBaseUrl('https://gateway.example/api/',true),'https://gateway.example/api'));
test('Gateway URL cannot contain credentials or query strings',()=>{for(const value of ['https://user:secret@gateway.example/api','https://gateway.example/api?key=secret','https://gateway.example/api#fragment'])assert.throws(()=>resolveApiBaseUrl(value,true));});
test('Gateway URL must include the API prefix',()=>assert.throws(()=>resolveApiBaseUrl('https://gateway.example',true),/end in/));
