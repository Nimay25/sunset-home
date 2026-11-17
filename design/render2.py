import cv2, numpy as np
m = cv2.imread('marm_mountains.png', cv2.IMREAD_UNCHANGED).astype(np.float32)/255
h,w = m.shape[:2]
c = cv2.resize(cv2.imread('clouds.png',0),(w,h),interpolation=cv2.INTER_CUBIC).astype(np.float32)/255
gl = cv2.resize(cv2.imread('glow.png',0),(w,h),interpolation=cv2.INTER_CUBIC).astype(np.float32)/255
def hx(s): s=s.lstrip('#'); return np.array([int(s[4:6],16),int(s[2:4],16),int(s[0:2],16)],np.float32)/255
def lin(stops):
    yy=np.linspace(0,1,h)[:,None]; out=np.zeros((h,1,3),np.float32)
    for (p0,c0),(p1,c1) in zip(stops,stops[1:]):
        t=np.clip((yy-p0)/(p1-p0+1e-6),0,1)[...,None]; sel=((yy>=p0)&(yy<=p1))[...,None]; out=np.where(sel,c0*(1-t)+c1*t,out)
    return np.repeat(out,w,1)
def ov(b,s): return np.where(b<.5,2*b*s,1-2*(1-b)*(1-s))
def render(p):
    s1,s2,s3,s4,hl,rim=[hx(x) for x in p]
    sky=lin([(0,s1),(.16,s2),(.32,s3),(.46,s4),(1,s4)])
    yy,xx=np.mgrid[0:h,0:w]; d=np.sqrt(((xx/w-.62)/.42)**2+((yy/h-.44)/.2)**2); g=np.clip(1-d,0,1)[...,None]**1.5
    sky=sky*(1-g)+s4*g
    sky=sky*.12+ov(sky,c[...,None])*.88
    ga=gl[...,None]*.75; sky=sky*(1-ga)+(1-(1-sky)*(1-hl))*ga
    a=m[...,3:4]; img=sky*(1-a)+m[...,:3]*a
    tint=lin([(0,rim),(.3,rim),(.8,s2),(1,s2)])
    img=img*(1-a*.4)+ov(img,tint)*a*.4
    sh=np.interp(np.linspace(0,1,h),[0,.2,.6,1],[.25,0,0,.55])[:,None,None]; img=img*(1-sh)
    return (np.clip(img,0,1)*255).astype(np.uint8)
P={'Ember (YouTube)':['#14030a','#6e0b1a','#e5231b','#ffb347','#ff4a2e','#ff5a3a'],
   'Aurora (Hulu)':['#010f12','#06463f','#1ce783','#e6ff8a','#39ffa0','#3df2a0'],
   'Ultraviolet (Max)':['#08061f','#2a1d8f','#6a5cff','#ff9ad5','#8f7dff','#7a6bff'],
   'Alpenglow (default)':['#1e2350','#5a3f8a','#e0607e','#ffb36b','#ff7a6b','#ff9a6b']}
t=[]
for k,p in P.items():
    im=cv2.resize(render(p),(800,450),interpolation=cv2.INTER_AREA); cv2.putText(im,k,(12,28),cv2.FONT_HERSHEY_SIMPLEX,.75,(255,255,255),2,cv2.LINE_AA); t.append(im)
cv2.imwrite('skies-preview.jpg',np.vstack([np.hstack(t[:2]),np.hstack(t[2:])]))
