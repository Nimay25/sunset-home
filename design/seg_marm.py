import cv2, numpy as np
im = cv2.imread('photos/marmolada-sunset-1920.jpg')[0:1080]
h,w = im.shape[:2]
ridge=[(0,535),(60,530),(110,525),(140,515),(170,480),(200,467),(240,472),(270,485),(300,490),(340,470),(390,430),(400,420),(450,418),(560,418),(590,425),(620,450),(640,465),(670,460),(690,465),(700,475),(730,480),(750,500),(780,495),(810,505),(830,525),(850,555),(880,555),(910,550),(950,550),(980,545),(1010,505),(1025,497),(1050,445),(1090,415),(1140,345),(1200,320),(1260,295),(1290,290),(1340,298),(1370,305),(1410,320),(1435,340),(1460,328),(1500,345),(1540,365),(1590,380),(1640,400),(1675,412),(1700,420),(1720,445),(1730,462),(1770,462),(1820,450),(1840,435),(1880,450),(1915,465),(w,468)]
poly=np.array(ridge+[(w,h),(0,h)],np.int32)
rough=np.zeros((h,w),np.uint8); cv2.fillPoly(rough,[poly],255)
B=18; k=cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(2*B+1,2*B+1))
mask=np.full((h,w),cv2.GC_BGD,np.uint8)
mask[cv2.dilate(rough,k)>0]=cv2.GC_PR_BGD; mask[rough>0]=cv2.GC_PR_FGD; mask[cv2.erode(rough,k)>0]=cv2.GC_FGD
bg=np.zeros((1,65));fg=np.zeros((1,65))
cv2.grabCut(im,mask,None,bg,fg,8,cv2.GC_INIT_WITH_MASK)
fgm=np.where((mask==1)|(mask==3),255,0).astype(np.uint8)
n,comp=cv2.connectedComponents(fgm); keep=set(comp[-1][comp[-1]>0].tolist()); fgm=np.isin(comp,list(keep)).astype(np.uint8)*255
n,comp=cv2.connectedComponents(255-fgm); topl=set(comp[0][comp[0]>0].tolist()); fgm=255-np.isin(comp,list(topl)).astype(np.uint8)*255
alpha=cv2.GaussianBlur(fgm,(3,3),0)
rgba=cv2.cvtColor(im,cv2.COLOR_BGR2BGRA); rgba[...,3]=alpha
cv2.imwrite('marm_mountains.png',rgba)
S=''
a=(alpha/255.)[...,None]; dbg=(im*a+np.array([0,255,0])*(1-a)).astype(np.uint8)
cv2.imwrite('cutout-check.jpg',cv2.resize(dbg,(960,540)))
z1=cv2.resize(dbg[260:600,1000:1920],None,fx=1,fy=1); z2=dbg[380:600,0:920]
cv2.imwrite('cutout-edges.jpg',np.vstack([z1,cv2.copyMakeBorder(z2,0,0,0,0,cv2.BORDER_CONSTANT)]))
print('sky frac',(alpha<128).mean())
