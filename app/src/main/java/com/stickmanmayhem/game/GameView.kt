package com.stickmanmayhem.game

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

private data class Fighter(var x: Float, var y: Float, var vx: Float = 0f, var vy: Float = 0f, var hp: Float = 100f, var facing: Int = 1, var grounded: Boolean = false, var attack: Float = 0f, var shoot: Float = 0f, var hurt: Float = 0f)
private data class Shot(var x: Float, var y: Float, var vx: Float, val owner: Int, var life: Float = 1.6f)

class GameView(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val red = Fighter(180f, 600f)
    private val blue = Fighter(700f, 600f, facing = -1)
    private val shots = mutableListOf<Shot>()
    private val platforms = listOf(RectF(0f, 600f, 960f, 680f), RectF(90f, 470f, 300f, 500f), RectF(390f, 390f, 590f, 420f), RectF(680f, 470f, 870f, 500f))
    private val buttons = mutableMapOf<String, RectF>()
    private var scale = 1f; private var ox = 0f; private var oy = 0f
    private var last = System.nanoTime(); private var elapsed = 0f
    private var left = false; private var right = false; private var jump = false; private var hit = false; private var fire = false
    private var wasJump = false; private var wasHit = false; private var wasFire = false
    private var ended = false; private var winner = ""; private var paused = false

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val dt = ((System.nanoTime() - last) / 1e9).toFloat().coerceIn(0f, .033f); last = System.nanoTime()
        scale = min(width / 960f, height / 680f); ox = (width - 960f * scale) / 2f; oy = (height - 680f * scale) / 2f
        c.save(); c.translate(ox, oy); c.scale(scale, scale)
        drawWorld(c)
        if (!ended && !paused) update(dt)
        platforms.forEach { p -> paint.color = Color.rgb(101, 202, 64); c.drawRoundRect(p, 8f, 8f, paint); paint.color = Color.rgb(44, 132, 55); c.drawRect(p.left, p.bottom-8, p.right, p.bottom, paint); paint.color = Color.rgb(180, 238, 105); c.drawRect(p.left, p.top, p.right, p.top+7, paint) }
        shots.forEach { s -> paint.color = if (s.owner == 0) Color.rgb(255,90,70) else Color.CYAN; c.drawCircle(s.x,s.y,8f,paint) }
        drawFighter(c, red, Color.rgb(242,48,55), true); drawFighter(c, blue, Color.rgb(30,145,255), false)
        drawHud(c); drawControls(c)
        if (paused) overlay(c,"PAUSED","Tap to resume")
        if (ended) overlay(c,"$winner WINS!","Tap anywhere to restart")
        c.restore(); postInvalidateOnAnimation()
    }

    private fun drawWorld(c: Canvas) {
        paint.shader = LinearGradient(0f,0f,0f,680f,Color.rgb(15,29,73),Color.rgb(39,75,125),Shader.TileMode.CLAMP); c.drawRect(0f,0f,960f,680f,paint); paint.shader=null
        for(i in 0..13){ val x=i*78f; val h=90f+(i*47%150); paint.color=Color.rgb(23,39,77); c.drawRect(x,590-h,x+54,590f,paint) }
        paint.color=Color.rgb(13,27,55); c.drawRect(0f,600f,960f,680f,paint)
        paint.color=Color.rgb(100,140,205); for(i in 0..30)c.drawCircle((i*83%950).toFloat(),(35+i*61%220).toFloat(),2f,paint)
    }

    private fun update(dt: Float) {
        elapsed += dt
        if (jump && !wasJump && red.grounded) { red.vy=-520f; red.grounded=false }
        if (hit && !wasHit && red.attack<=0f) red.attack=.28f
        if (fire && !wasFire && red.shoot<=0f) { shots.add(Shot(red.x+red.facing*28,red.y-45,red.facing*520f,0)); red.shoot=.55f }
        wasJump=jump; wasHit=hit; wasFire=fire
        red.vx=when { left -> -235f; right -> 235f; else -> 0f }; red.facing=if(blue.x>=red.x)1 else -1
        val dx=red.x-blue.x; val dist=abs(dx); blue.facing=if(dx>=0)1 else -1
        blue.vx=when { dist>175f -> sign(dx)*155f; dist<72f -> -sign(dx)*85f; else -> 0f }
        if(blue.grounded && dist<230f && elapsed%2.7f<dt){blue.vy=-470f;blue.grounded=false}
        if(dist<88f && blue.attack<=0f && elapsed%1.1f<dt)blue.attack=.32f
        if(dist in 180f..480f && blue.shoot<=0f && elapsed%2.2f<dt){shots.add(Shot(blue.x+blue.facing*28,blue.y-45,blue.facing*390f,1));blue.shoot=1.5f}
        move(red,dt); move(blue,dt)
        if(red.attack>0f){red.attack-=dt;if(red.attack>.16f && abs(red.x-blue.x)<86f && abs(red.y-blue.y)<65f){damage(blue,12f);red.attack=.15f}}
        if(blue.attack>0f){blue.attack-=dt;if(blue.attack>.18f && abs(red.x-blue.x)<86f && abs(red.y-blue.y)<65f){damage(red,9f);blue.attack=.16f}}
        val it=shots.iterator();while(it.hasNext()){val s=it.next();s.x+=s.vx*dt;s.life-=dt;val target=if(s.owner==0)blue else red;if(abs(s.x-target.x)<28f&&abs((target.y-45)-s.y)<42f){damage(target,8f);it.remove()}else if(s.life<=0||s.x<0||s.x>960)it.remove()}
        red.attack=max(0f,red.attack);blue.attack=max(0f,blue.attack);red.shoot=max(0f,red.shoot);blue.shoot=max(0f,blue.shoot)
        if(red.hp<=0||blue.hp<=0){ended=true;winner=if(red.hp>blue.hp)"RED" else "BLUE"}
    }
    private fun damage(f:Fighter,amount:Float){if(f.hurt<=0){f.hp=max(0f,f.hp-amount);f.hurt=.25f;f.vx+=f.facing*-95f}}
    private fun move(f:Fighter,dt:Float){f.hurt=max(0f,f.hurt-dt);f.x=(f.x+f.vx*dt).coerceIn(35f,925f);val old=f.y;f.vy+=1150f*dt;f.y+=f.vy*dt;f.grounded=false;platforms.forEach{r->if(f.vy>=0&&old<=r.top&&f.y>=r.top&&f.x>r.left-22&&f.x<r.right+22){f.y=r.top;f.vy=0f;f.grounded=true}};if(f.y>635){f.y=635f;f.vy=0f;f.grounded=true}}

    private fun drawFighter(c:Canvas,f:Fighter,color:Int,isRed:Boolean){val x=f.x;val y=f.y;paint.color=color;paint.strokeWidth=13f;paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND;paint.style=Paint.Style.STROKE;c.drawLine(x,y-26,x-17,y-3,paint);c.drawLine(x,y-26,x+18,y-3,paint);c.drawLine(x,y-70,x,y-28,paint);val reach=if(f.attack>0)40f else 23f;c.drawLine(x,y-62,x+f.facing*reach,y-48,paint);c.drawLine(x,y-62,x-f.facing*20,y-43,paint);paint.style=Paint.Style.FILL;c.drawCircle(x,y-88,20f,paint);paint.color=Color.WHITE;c.drawCircle(x+f.facing*7,y-92,4f,paint);paint.color=Color.BLACK;c.drawCircle(x+f.facing*8,y-92,2f,paint);paint.color=if(isRed)Color.WHITE else Color.BLACK;c.drawRoundRect(x-18,y-(if(isRed)108 else 112),x+18,y-(if(isRed)101 else 105),4f,4f,paint)}
    private fun drawHud(c:Canvas){health(c,25f,28f,red.hp,Color.RED,false);health(c,935f,28f,blue.hp,Color.BLUE,true);paint.color=Color.WHITE;paint.textSize=20f;paint.typeface=Typeface.DEFAULT_BOLD;paint.textAlign=Paint.Align.CENTER;c.drawText("STICKMAN MAYHEM",480f,34f,paint)}
    private fun health(c:Canvas,x:Float,y:Float,hp:Float,color:Int,rightSide:Boolean){val l=if(rightSide)x-190 else x;paint.color=Color.argb(180,5,12,30);c.drawRoundRect(l,y,l+190,y+26,12f,12f,paint);paint.color=color;val w=180*hp/100f;c.drawRoundRect(if(rightSide)l+185-w else l+5,y+5,if(rightSide)l+185 else l+5+w,y+21,8f,8f,paint);paint.color=Color.WHITE;paint.textSize=15f;paint.textAlign=if(rightSide)Paint.Align.RIGHT else Paint.Align.LEFT;c.drawText("${if(rightSide)"BLUE" else "RED"} ${hp.toInt()}%",if(rightSide)x else x+5,y-4,paint)}
    private fun drawControls(c:Canvas){buttons.clear();fun b(n:String,r:RectF,t:String,col:Int){buttons[n]=r;paint.color=Color.argb(165,8,18,39);c.drawRoundRect(r,16f,16f,paint);paint.color=col;paint.textSize=27f;paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.DEFAULT_BOLD;c.drawText(t,r.centerX(),r.centerY()+9,paint)};b("left",RectF(20f,570f,92f,645f),"◀",Color.WHITE);b("right",RectF(100f,570f,172f,645f),"▶",Color.WHITE);b("jump",RectF(780f,570f,850f,645f),"↑",Color.WHITE);b("attack",RectF(865f,570f,935f,645f),"⚔",Color.rgb(255,100,100));b("shoot",RectF(865f,485f,935f,555f),"●",Color.CYAN);b("pause",RectF(445f,48f,515f,88f),"Ⅱ",Color.WHITE)}
    private fun overlay(c:Canvas,title:String,sub:String){paint.color=Color.argb(195,5,10,25);c.drawRect(0f,0f,960f,680f,paint);paint.color=Color.WHITE;paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.DEFAULT_BOLD;paint.textSize=56f;c.drawText(title,480f,300f,paint);paint.textSize=24f;c.drawText(sub,480f,350f,paint)}
    override fun onTouchEvent(e:MotionEvent):Boolean{val x=(e.x-ox)/scale;val y=(e.y-oy)/scale;if(e.action==MotionEvent.ACTION_DOWN&&(ended||paused)){if(ended)reset()else paused=false;return true};left=false;right=false;jump=false;hit=false;fire=false;for(i in 0 until e.pointerCount){val px=(e.getX(i)-ox)/scale;val py=(e.getY(i)-oy)/scale;val k=buttons.entries.firstOrNull{it.value.contains(px,py)}?.key;when(k){"left"->left=true;"right"->right=true;"jump"->jump=true;"attack"->hit=true;"shoot"->fire=true;"pause"->if(e.action==MotionEvent.ACTION_DOWN)paused=!paused}};return true}
    private fun reset(){red.x=180f;red.y=600f;red.vx=0f;red.vy=0f;red.hp=100f;red.attack=0f;red.shoot=0f;blue.x=700f;blue.y=600f;blue.vx=0f;blue.vy=0f;blue.hp=100f;blue.attack=0f;blue.shoot=0f;shots.clear();ended=false;winner="";elapsed=0f}
}
