package com.atom.chat.fun;

/** Normalized-screen art equations, independent of widget layout and UI density. */
final class ExperimentalEffectShader {
    private ExperimentalEffectShader() { }
    static final String SOURCE="""
        uniform shader scene;
        uniform float2 size;
        uniform float progress;
        uniform float mode;
        uniform float displayRgb;
        float square(float x) { return x*x; }
        float hash(float2 p) { return fract(sin(dot(p,float2(127.1,311.7)))*43758.5453); }
        float noise(float2 p) {
            float2 i=floor(p),f=fract(p); f=f*f*(3.0-2.0*f);
            return mix(mix(hash(i),hash(i+float2(1,0)),f.x),mix(hash(i+float2(0,1)),hash(i+1),f.x),f.y);
        }
        float cloud(float2 p) {
            float n=0.0,a=0.5;
            for(int i=0;i<4;i++){n+=noise(p)*a;p=float2(p.x*1.6-p.y*1.2,p.x*1.2+p.y*1.6)+7.0;a*=0.5;}
            return n;
        }
        half4 main(float2 p) {
            float t=progress;
            float e=smoothstep(0.0,0.12,t)*(1.0-smoothstep(0.68,1.0,t));
            float unit=min(size.x,size.y);
            float2 uv=p/size,q=(p-size*0.5)/unit;
            float r=length(q),angle=atan(q.y,q.x);
            float2 radial=q/max(r,0.001),offset=float2(0);
            float3 glow=float3(0),tint=float3(0.04,0.12,0.23);
            float veil=0.045*e,black=0.0;
            if(mode<1.5) {
                float front=0.04+1.15*(1.0-pow(1.0-t,2.0));
                float band=exp(-square((r-front)*27.0));
                offset+=radial*sin((r-front)*110.0)*band*0.026*unit*e;
                glow+=mix(float3(0.20,0.42,1.0),float3(0.12,0.95,1.0),band)*band*0.75*e;
            }
            if(mode>1.5&&mode<2.5) {
                float edge=min(min(uv.x,1.0-uv.x)*size.x,min(uv.y,1.0-uv.y)*size.y)/unit;
                float n=cloud(q*8.0+float2(t*0.15,0));
                float growth=0.12+0.27*sin(t*3.14159);
                float ice=(1.0-smoothstep(growth-0.07,growth+0.06,edge+n*0.14))*e;
                float crystal=pow(abs(sin(q.x*53.0+q.y*31.0+n*8.0)*sin(q.y*61.0-q.x*27.0)),10.0);
                offset=float2(n-0.5,noise(q*17.0)-0.5)*unit*0.012*ice;
                glow=float3(0.38,0.74,1.0)*ice*(0.15+crystal*0.8);
                veil=0.34*ice;tint=float3(0.10,0.27,0.40);
            }
            if(mode<0.5||mode>2.5) {
                float spin=angle+0.065*e/max(r,0.06);
                float bent=r+0.022*e/(r+0.13);
                offset+=(float2(cos(spin),sin(spin))*bent-q)*unit*e;
                float2 disk=float2(q.x,q.y*3.6);
                float dr=length(disk);
                float wisps=cloud(float2(angle*2.0-t*2.0,dr*13.0));
                float ring=exp(-square((dr-0.24)*22.0));
                float halo=exp(-r*5.5);
                float volume=0.0;
                for(int i=0;i<8;i++) {
                    float z=float(i)*0.035;
                    float d=length(float2(q.x,(q.y+z*0.20)*3.6));
                    volume+=exp(-abs(d-0.24-z*0.12)*65.0)*(0.5+0.5*sin(angle*9.0+t*8.0+z*35.0));
                }
                glow+=mix(float3(0.28,0.20,0.95),float3(0.12,0.85,1.0),wisps)*(ring*(0.5+wisps)+volume*0.12+halo*0.14)*e;
                black=(1.0-smoothstep(0.075,0.10,r))*0.95*e;
                veil+=halo*0.15*e;
            }
            if(mode<0.5) {
                float n=cloud(q*3.5+float2(t*0.3,-t*0.25));
                float nebula=pow(n,3.0)*0.5*e;
                glow+=mix(float3(0.43,0.13,0.85),float3(0.08,0.75,0.95),n)*nebula;
            }
            float2 sampleAt=clamp(p+offset,float2(0.5),size-0.5);
            half4 base=scene.eval(sampleAt);
            float alpha=displayRgb>0.5?1.0:float(base.a);
            float3 straight=displayRgb>0.5?float3(base.rgb):float3(base.rgb)/max(alpha,0.0001);
            float3 color=mix(straight,tint,veil)*(1.0-black)+glow;
            return half4(half3(clamp(color,0.0,1.0)*alpha),half(alpha));
        }
        """;
}
