package com.example.mysignupapp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Static data source for all First Aid tips.
 * No internet connection required — works fully offline.
 */
public class FirstAidData {

    public static List<FirstAid_model> getAllTips() {
        List<FirstAid_model> tips = new ArrayList<>();

        // ─── CARDIAC ──────────────────────────────────────────────────────────
        tips.add(new FirstAid_model(
                "cpr_adult",
                "CPR — Adult",
                "Cardiac",
                "Cardiopulmonary resuscitation for an unresponsive adult",
                "critical",
                "❤️",
                Arrays.asList(
                        "Check the scene is safe, then check if the person is responsive — tap shoulders and shout their name.",
                        "Call emergency services (or tell someone else to call) immediately.",
                        "Lay the person flat on their back on a firm surface.",
                        "Tilt the head back gently and lift the chin to open the airway.",
                        "Look, listen and feel for breathing for no more than 10 seconds.",
                        "If not breathing normally, place the heel of one hand on the centre of the chest (lower half of breastbone).",
                        "Place your other hand on top and interlace fingers. Keep arms straight.",
                        "Press down hard and fast — 5–6 cm depth, 100–120 compressions per minute (to the beat of 'Stayin' Alive').",
                        "After 30 compressions, give 2 rescue breaths: pinch nose, seal your mouth over theirs, blow for 1 second until chest rises.",
                        "Continue 30:2 cycle until emergency services arrive, an AED is available, or the person starts breathing normally."
                ),
                Arrays.asList(
                        "Do NOT stop CPR unless the person recovers or a professional takes over.",
                        "Do NOT tilt the head if a spinal injury is suspected.",
                        "Do NOT give rescue breaths if you are untrained — do hands-only CPR instead (continuous compressions)."
                ),
                "always"
        ));

        tips.add(new FirstAid_model(
                "heart_attack",
                "Heart Attack",
                "Cardiac",
                "Recognising and responding to a suspected heart attack",
                "critical",
                "🫀",
                Arrays.asList(
                        "Recognise the signs: crushing chest pain or pressure, pain radiating to arm/jaw/neck, shortness of breath, sweating, nausea.",
                        "Call emergency services immediately — do not drive the person yourself.",
                        "Help the person sit or lie in a comfortable position — usually sitting up with knees bent (W position) eases breathing.",
                        "Loosen any tight clothing around the neck, chest, and waist.",
                        "If the person is conscious and not allergic, give one adult aspirin (300 mg) to chew slowly — not swallow whole.",
                        "Stay calm, reassure the person, and keep them warm with a blanket.",
                        "Monitor breathing and pulse. If they become unresponsive and stop breathing, begin CPR immediately.",
                        "Be ready to use an AED if one is available."
                ),
                Arrays.asList(
                        "Do NOT let the person walk around or exert themselves.",
                        "Do NOT give aspirin if they are allergic or if a doctor has advised against it.",
                        "Do NOT leave the person alone."
                ),
                "always"
        ));

        // ─── BREATHING ────────────────────────────────────────────────────────
        tips.add(new FirstAid_model(
                "choking_adult",
                "Choking — Adult",
                "Breathing",
                "Clearing a blocked airway in a conscious adult",
                "critical",
                "🫁",
                Arrays.asList(
                        "Ask the person to cough forcefully — if they can cough, encourage them to keep coughing.",
                        "If coughing fails, lean them forward and give up to 5 firm back blows between the shoulder blades with the heel of your hand.",
                        "Check the mouth after each blow — only remove an object if you can clearly see it.",
                        "If back blows fail, give up to 5 abdominal thrusts (Heimlich manoeuvre): stand behind them, place one foot forward, make a fist above the navel, cover with other hand, and thrust sharply inward and upward.",
                        "Alternate 5 back blows and 5 abdominal thrusts until the object clears or the person becomes unconscious.",
                        "If the person becomes unconscious, lower them to the floor and begin CPR. Each time you open the airway for rescue breaths, look for the object and remove if visible.",
                        "Call emergency services if the obstruction does not clear quickly."
                ),
                Arrays.asList(
                        "Do NOT perform blind finger sweeps — only remove an object you can clearly see.",
                        "Do NOT give abdominal thrusts to pregnant women or obese persons — use chest thrusts instead.",
                        "Do NOT leave the person alone."
                ),
                "always"
        ));

        tips.add(new FirstAid_model(
                "choking_infant",
                "Choking — Infant (under 1 year)",
                "Breathing",
                "Clearing a blocked airway in a baby under 12 months",
                "critical",
                "👶",
                Arrays.asList(
                        "Hold the baby face-down along your forearm, supporting the head lower than the chest.",
                        "Give 5 firm back blows between the shoulder blades using the heel of your hand.",
                        "Turn the baby face-up on your forearm, supporting the head.",
                        "Give 5 chest thrusts using 2 fingers on the centre of the chest, just below the nipple line. Push down about 1.5 cm.",
                        "Check the mouth after each cycle. Remove an object only if clearly visible — never do a blind sweep.",
                        "Alternate 5 back blows and 5 chest thrusts.",
                        "If the baby becomes unconscious, begin infant CPR and call emergency services immediately."
                ),
                Arrays.asList(
                        "Do NOT give abdominal thrusts to an infant — use chest thrusts only.",
                        "Do NOT shake the baby.",
                        "Do NOT perform blind finger sweeps."
                ),
                "always"
        ));

        tips.add(new FirstAid_model(
                "asthma_attack",
                "Asthma Attack",
                "Breathing",
                "Helping someone having a severe asthma attack",
                "high",
                "💨",
                Arrays.asList(
                        "Stay calm and keep the person calm — panic worsens breathing.",
                        "Help them sit upright, slightly leaning forward — do NOT lay them down.",
                        "Help them use their reliever inhaler (usually blue) — 1 puff every 30–60 seconds, up to 10 puffs.",
                        "If they have a spacer, use it — it delivers medicine more effectively.",
                        "If no improvement after 10 puffs, call emergency services.",
                        "While waiting, repeat inhaler puffs every 15 minutes.",
                        "If the person stops breathing, begin CPR."
                ),
                Arrays.asList(
                        "Do NOT lay the person flat.",
                        "Do NOT leave them alone.",
                        "Do NOT use a preventer inhaler (usually brown/purple) — only the reliever (blue) gives fast relief."
                ),
                "if_worsens"
        ));

        // ─── BLEEDING ────────────────────────────────────────────────────────
        tips.add(new FirstAid_model(
                "severe_bleeding",
                "Severe Bleeding",
                "Bleeding",
                "Controlling life-threatening external blood loss",
                "critical",
                "🩸",
                Arrays.asList(
                        "Call emergency services immediately for severe or spurting bleeding.",
                        "Put on gloves if available. If not, use a plastic bag or extra clothing as a barrier.",
                        "Apply firm, direct pressure to the wound using a clean cloth, pad, or bandage.",
                        "Press hard and continuously — do not lift the pad to check. If it soaks through, add more material on top.",
                        "If the wound is on a limb and bleeding is life-threatening, apply a tourniquet 5–7 cm above the wound. Note the time applied.",
                        "Elevate the injured limb above heart level if possible and no fracture is suspected.",
                        "Keep the person warm, still, and reassured. Lay them down to prevent fainting.",
                        "Monitor for shock: pale/cold/clammy skin, rapid weak pulse, confusion. If present, raise legs 30 cm (unless head/spine injury)."
                ),
                Arrays.asList(
                        "Do NOT remove an embedded object — apply pressure around it.",
                        "Do NOT remove the first dressing — add more on top.",
                        "Do NOT apply a tourniquet to the neck, chest, or abdomen."
                ),
                "always"
        ));

        tips.add(new FirstAid_model(
                "nosebleed",
                "Nosebleed",
                "Bleeding",
                "Stopping a nosebleed safely",
                "low",
                "👃",
                Arrays.asList(
                        "Sit the person upright and lean slightly forward — not backward.",
                        "Pinch the soft part of the nose (just below the bony bridge) firmly.",
                        "Hold continuously for 10–15 minutes without releasing to check.",
                        "Breathe through the mouth during this time.",
                        "Apply a cold compress or ice pack wrapped in cloth to the nose and cheeks.",
                        "After bleeding stops, advise the person not to blow their nose for several hours.",
                        "Seek medical attention if bleeding does not stop after 20 minutes or if the nosebleed followed a head injury."
                ),
                Arrays.asList(
                        "Do NOT tilt the head backward — blood may flow into the throat causing nausea.",
                        "Do NOT pack the nose with tissue tightly.",
                        "Do NOT blow the nose immediately after it stops — this dislodges the clot."
                ),
                "rarely"
        ));

        // ─── BURNS ───────────────────────────────────────────────────────────
        tips.add(new FirstAid_model(
                "burns",
                "Burns & Scalds",
                "Burns",
                "Treating thermal burns from heat, steam, or hot liquids",
                "high",
                "🔥",
                Arrays.asList(
                        "Remove the person from the source of the burn if safe to do so.",
                        "Cool the burn immediately under cool (not cold/icy) running water for at least 20 minutes — start within 3 hours of the burn.",
                        "Remove jewellery, watches, and clothing near the burn — but NOT if they are stuck to the skin.",
                        "Cover the burn loosely with a clean, non-fluffy material — cling film (plastic wrap) works well. Do not wrap tightly.",
                        "Give paracetamol or ibuprofen for pain relief if appropriate.",
                        "Call emergency services or go to hospital for: burns larger than the person's palm, burns on face/hands/feet/genitals/joints, chemical or electrical burns, burns with white/brown/black skin, burns in children or elderly."
                ),
                Arrays.asList(
                        "Do NOT use ice, iced water, butter, toothpaste, or creams on a burn.",
                        "Do NOT burst blisters — this increases infection risk.",
                        "Do NOT use fluffy cotton wool or anything that sticks to the wound.",
                        "Do NOT remove clothing that is stuck to the burn."
                ),
                "if_worsens"
        ));

        tips.add(new FirstAid_model(
                "chemical_burn",
                "Chemical Burn",
                "Burns",
                "First aid for burns caused by chemicals or corrosives",
                "high",
                "⚗️",
                Arrays.asList(
                        "Call emergency services immediately for any chemical burn.",
                        "Protect yourself — wear gloves and avoid contact with the chemical.",
                        "Remove contaminated clothing and jewellery carefully — do not pull over the head.",
                        "Flush the affected area with large amounts of cool running water for at least 20 minutes.",
                        "If the chemical is in the eye, irrigate with water for at least 20 minutes, holding the eyelid open.",
                        "Cover the burn with a clean dressing after flushing.",
                        "Take note of the chemical name to inform medical staff."
                ),
                Arrays.asList(
                        "Do NOT try to neutralise the chemical with another substance.",
                        "Do NOT rub the affected area.",
                        "Do NOT allow the run-off water to contact other parts of the body."
                ),
                "always"
        ));

        // ─── FRACTURES ───────────────────────────────────────────────────────
        tips.add(new FirstAid_model(
                "fracture",
                "Broken Bone (Fracture)",
                "Fractures",
                "Stabilising a suspected broken bone",
                "high",
                "🦴",
                Arrays.asList(
                        "Tell the person not to move the injured limb.",
                        "If there is an open wound (bone visible or skin broken), cover it loosely with a clean, sterile dressing. Do not push bone back in.",
                        "Immobilise the fracture: use padding (clothing, foam) around the injury, then splint if needed — tie the injured limb to an uninjured body part or straight object.",
                        "Tie splint bandages above and below the fracture site — never over it.",
                        "Check circulation below the injury regularly (colour, warmth, sensation, pulse).",
                        "Apply an ice pack wrapped in cloth to reduce swelling.",
                        "Call emergency services if: the fracture is in the hip, pelvis, or spine; the person cannot move; there is an open fracture; circulation is compromised."
                ),
                Arrays.asList(
                        "Do NOT try to straighten a fractured bone.",
                        "Do NOT move the person unless absolutely necessary.",
                        "Do NOT give food or drink in case surgery is needed."
                ),
                "if_worsens"
        ));

        tips.add(new FirstAid_model(
                "spinal_injury",
                "Suspected Spinal Injury",
                "Fractures",
                "Caring for someone with a possible spine or neck injury",
                "critical",
                "🫷",
                Arrays.asList(
                        "Do NOT move the person unless they are in immediate danger.",
                        "Call emergency services immediately.",
                        "Keep the person as still as possible. If conscious, reassure them and tell them not to move.",
                        "Kneel behind the person's head. Hold the head gently but firmly in the position you found it — do not try to straighten.",
                        "Support the head until emergency services arrive.",
                        "If the person vomits and you must roll them, use the log-roll technique with multiple people keeping spine aligned.",
                        "Monitor breathing and consciousness continuously.",
                        "If breathing stops, begin CPR — the risk of spinal cord damage is less critical than stopping breathing."
                ),
                Arrays.asList(
                        "Do NOT move or twist the head or neck.",
                        "Do NOT allow the person to sit up or stand.",
                        "Do NOT remove a helmet unless breathing is compromised and you cannot manage the airway otherwise."
                ),
                "always"
        ));

        // ─── SHOCK ───────────────────────────────────────────────────────────
        tips.add(new FirstAid_model(
                "shock",
                "Shock",
                "Shock",
                "Managing circulatory shock from injury or severe blood loss",
                "critical",
                "😰",
                Arrays.asList(
                        "Call emergency services immediately.",
                        "Identify and treat the cause if possible (e.g. stop severe bleeding).",
                        "Lay the person down on a flat surface.",
                        "Raise the legs about 30 cm above heart level — unless there is a head, neck, back, leg, or pelvic injury.",
                        "Do NOT raise legs if raising them causes pain.",
                        "Keep the person warm with a blanket or coat.",
                        "Loosen tight clothing at the neck, chest, and waist.",
                        "Do not give anything to eat or drink.",
                        "Monitor breathing, pulse, and level of consciousness every few minutes.",
                        "If the person becomes unconscious and stops breathing, begin CPR."
                ),
                Arrays.asList(
                        "Do NOT let the person walk around.",
                        "Do NOT give food, drink, or medication by mouth.",
                        "Do NOT apply a heat source directly — use a blanket only."
                ),
                "always"
        ));

        // ─── POISONING ───────────────────────────────────────────────────────
        tips.add(new FirstAid_model(
                "poisoning",
                "Poisoning (Swallowed)",
                "Poisoning",
                "Responding to someone who has swallowed a toxic substance",
                "high",
                "☠️",
                Arrays.asList(
                        "Call emergency services or Poison Control immediately. Have the substance container available.",
                        "If the person is conscious, ask what they took, how much, and when.",
                        "Keep the person calm and still.",
                        "If they are unconscious, place them in the recovery position and monitor breathing.",
                        "If they stop breathing, begin CPR.",
                        "Collect any vomit in a container for medical staff to identify the poison."
                ),
                Arrays.asList(
                        "Do NOT induce vomiting unless specifically told to by Poison Control or a doctor.",
                        "Do NOT give food, drink, or milk unless advised.",
                        "Do NOT leave the person alone."
                ),
                "always"
        ));

        // ─── HEAD INJURIES ────────────────────────────────────────────────────
        tips.add(new FirstAid_model(
                "head_injury",
                "Head Injury",
                "Head & Brain",
                "Assessing and managing a head injury",
                "high",
                "🧠",
                Arrays.asList(
                        "Call emergency services if the person lost consciousness, is confused, has a seizure, has a visible skull fracture, is vomiting repeatedly, or has clear fluid from nose/ears.",
                        "Keep the person still and calm. Do not move if spinal injury is suspected.",
                        "Apply gentle pressure to any scalp wound with a clean cloth — do not press directly on a suspected skull fracture.",
                        "If conscious and no spinal injury suspected, help them sit or lie in a comfortable position.",
                        "Monitor every 15 minutes: level of consciousness, pupil size, breathing, pulse, speech.",
                        "Watch for worsening symptoms in the following 24–48 hours: worsening headache, confusion, unequal pupils, vomiting, weakness on one side."
                ),
                Arrays.asList(
                        "Do NOT give aspirin or ibuprofen — they increase bleeding risk. Paracetamol is safer.",
                        "Do NOT allow the person to return to sport or strenuous activity the same day.",
                        "Do NOT leave alone for at least 24 hours after a significant head injury."
                ),
                "if_worsens"
        ));

        // ─── STROKE ───────────────────────────────────────────────────────────
        tips.add(new FirstAid_model(
                "stroke",
                "Stroke — FAST Test",
                "Head & Brain",
                "Recognising stroke and acting immediately",
                "critical",
                "🧬",
                Arrays.asList(
                        "Use the FAST test — Face: Ask the person to smile. Does one side droop? Arms: Ask them to raise both arms. Does one drift down? Speech: Ask them to repeat a simple phrase. Is it slurred or strange? Time: If any sign is present, call emergency services IMMEDIATELY.",
                        "Note the exact time symptoms started — this is critical for treatment decisions.",
                        "Keep the person calm and still. Do not give food or drink.",
                        "If conscious, help them lie down with head and shoulders slightly raised.",
                        "If unconscious but breathing, place in recovery position.",
                        "If breathing stops, begin CPR.",
                        "Do not leave the person alone while waiting for help."
                ),
                Arrays.asList(
                        "Do NOT give aspirin — only doctors should decide this for strokes.",
                        "Do NOT let the person 'sleep it off' — time is brain cells.",
                        "Do NOT give food or water — the person may have difficulty swallowing."
                ),
                "always"
        ));

        // ─── SEIZURES ─────────────────────────────────────────────────────────
        tips.add(new FirstAid_model(
                "seizure",
                "Seizure / Epileptic Fit",
                "Neurological",
                "Safely managing a person during and after a seizure",
                "high",
                "⚡",
                Arrays.asList(
                        "Stay calm and time the seizure from the start.",
                        "Clear the area of anything hard or sharp that could cause injury.",
                        "Cushion the person's head with something soft — a folded jacket works well.",
                        "Do NOT restrain them. Gently guide limbs away from hard surfaces only.",
                        "Turn the person gently onto their side (recovery position) once convulsions ease.",
                        "Stay with them until fully recovered — they may be confused and tired afterwards (postictal state).",
                        "Call emergency services if: the seizure lasts more than 5 minutes; a second seizure follows; the person does not regain consciousness; they are injured; they are pregnant; it is their first seizure."
                ),
                Arrays.asList(
                        "Do NOT put anything in the person's mouth — they cannot swallow their tongue.",
                        "Do NOT restrain their movements.",
                        "Do NOT give water until fully conscious and able to swallow."
                ),
                "if_worsens"
        ));

        // ─── DIABETIC ─────────────────────────────────────────────────────────
        tips.add(new FirstAid_model(
                "hypoglycaemia",
                "Low Blood Sugar (Hypoglycaemia)",
                "Diabetic",
                "Treating a diabetic emergency with dangerously low blood sugar",
                "high",
                "🍬",
                Arrays.asList(
                        "Recognise symptoms: shakiness, sweating, confusion, pale skin, rapid heartbeat, hunger, or dizziness.",
                        "If conscious and able to swallow, give a fast-acting sugar: 150–200 ml fruit juice or regular (not diet) fizzy drink, 3–4 glucose tablets, or 3–4 teaspoons of sugar dissolved in water.",
                        "Have the person sit or lie down.",
                        "Re-check after 10–15 minutes. If symptoms persist, give sugar again.",
                        "Once recovered, give a longer-acting carbohydrate snack: a sandwich, crackers, or a glass of milk.",
                        "If the person is unconscious or cannot swallow, call emergency services immediately — do NOT put anything in their mouth.",
                        "Place an unconscious, breathing person in the recovery position."
                ),
                Arrays.asList(
                        "Do NOT give food or drink if the person cannot swallow safely.",
                        "Do NOT give a diet drink or artificial sweetener — it will not raise blood sugar.",
                        "Do NOT leave the person alone after symptoms develop."
                ),
                "if_worsens"
        ));

        // ─── EYE ──────────────────────────────────────────────────────────────
        tips.add(new FirstAid_model(
                "eye_injury",
                "Eye Injury (Foreign Object)",
                "Eye",
                "Removing a foreign object or treating an eye injury",
                "medium",
                "👁️",
                Arrays.asList(
                        "Tell the person not to rub the eye — this can scratch the cornea or embed the object deeper.",
                        "For a loose object (eyelash, grit): wash the eye gently with clean water or saline using a cup or eyewash station. Blink repeatedly underwater.",
                        "Pull the upper eyelid over the lower to encourage tears to flush the object out.",
                        "If the object is on the white of the eye and clearly visible, try to gently remove it with the corner of a clean, damp tissue.",
                        "For a penetrating or embedded object: do NOT remove it. Cover loosely with a sterile eye pad or clean cup. Seek emergency care immediately.",
                        "For chemical splashes: flush continuously with water for at least 20 minutes. Seek emergency care."
                ),
                Arrays.asList(
                        "Do NOT rub the eye.",
                        "Do NOT try to remove an object that is penetrating the eyeball.",
                        "Do NOT use tweezers or cotton wool directly on the eyeball."
                ),
                "if_worsens"
        ));

        // ─── ANAPHYLAXIS ──────────────────────────────────────────────────────
        tips.add(new FirstAid_model(
                "anaphylaxis",
                "Anaphylaxis (Severe Allergic Reaction)",
                "Allergic",
                "Emergency response to life-threatening allergic reaction",
                "critical",
                "🐝",
                Arrays.asList(
                        "Call emergency services immediately — anaphylaxis is life-threatening.",
                        "If the person has an adrenaline auto-injector (EpiPen), help them use it or administer it yourself: remove blue safety cap, place orange tip firmly against outer thigh (through clothing is fine), press and hold for 10 seconds.",
                        "Note the time of injection.",
                        "Lay the person flat with legs raised — unless breathing is difficult, in which case let them sit up.",
                        "A second EpiPen can be given after 5–15 minutes if symptoms return or do not improve.",
                        "Monitor breathing and consciousness continuously.",
                        "If breathing stops, begin CPR."
                ),
                Arrays.asList(
                        "Do NOT let the person stand or walk — sudden position change can be fatal in anaphylaxis.",
                        "Do NOT give antihistamines as the primary treatment — they act too slowly for anaphylaxis.",
                        "Do NOT leave the person alone."
                ),
                "always"
        ));

        // ─── HEAT / COLD ──────────────────────────────────────────────────────
        tips.add(new FirstAid_model(
                "heatstroke",
                "Heatstroke",
                "Heat & Cold",
                "Treating dangerous overheating of the body",
                "critical",
                "☀️",
                Arrays.asList(
                        "Call emergency services immediately — heatstroke is a medical emergency.",
                        "Move the person to a cool, shaded place.",
                        "Remove excess clothing.",
                        "Cool the person rapidly: apply cool water to skin, fan them, place ice packs wrapped in cloth on neck, armpits, and groin.",
                        "If conscious and able to swallow, give cool water to sip.",
                        "Do NOT give alcohol or caffeine.",
                        "Place unconscious person in recovery position and monitor breathing.",
                        "Continue cooling until body temperature drops or emergency services arrive."
                ),
                Arrays.asList(
                        "Do NOT use ice-cold water directly — rapid vasoconstriction can cause shock.",
                        "Do NOT give aspirin or paracetamol for heat-related illness.",
                        "Do NOT leave the person alone."
                ),
                "always"
        ));

        tips.add(new FirstAid_model(
                "hypothermia",
                "Hypothermia",
                "Heat & Cold",
                "Warming a person with dangerously low body temperature",
                "high",
                "🥶",
                Arrays.asList(
                        "Move the person to a warm, dry place. Handle gently — do not rub or massage.",
                        "Replace any wet clothing with dry blankets or clothing.",
                        "Insulate them from the ground with a sleeping bag, blankets, or foam.",
                        "Cover the head — a large proportion of heat is lost through the head.",
                        "Give warm (not hot) drinks and high-energy food if conscious and able to swallow.",
                        "Apply chemical heat packs wrapped in cloth to the armpits, neck, and groin — never directly on skin.",
                        "Call emergency services for moderate or severe hypothermia (confusion, slurred speech, shivering stopped).",
                        "Monitor breathing — if it stops, begin CPR."
                ),
                Arrays.asList(
                        "Do NOT rub or massage limbs — this sends cold blood to the core.",
                        "Do NOT give alcohol — it increases heat loss.",
                        "Do NOT place in a hot bath — rapid rewarming can cause cardiac arrhythmia."
                ),
                "if_worsens"
        ));

        // ─── DROWNING ─────────────────────────────────────────────────────────
        tips.add(new FirstAid_model(
                "drowning",
                "Near-Drowning",
                "Water",
                "Helping someone rescued from water",
                "critical",
                "🌊",
                Arrays.asList(
                        "Ensure your own safety first — do not enter fast or deep water without training. Use a rope, branch, or flotation device to pull the person out.",
                        "Call emergency services immediately.",
                        "Lay the person on their back on a firm surface.",
                        "Check for breathing. If not breathing, begin CPR immediately — start with 5 rescue breaths, then 30:2 compressions.",
                        "Continue CPR until the person breathes or emergency services arrive.",
                        "If breathing, place in recovery position.",
                        "Keep the person warm — remove wet clothing and wrap in blankets. Even in warm weather, wet skin causes rapid heat loss.",
                        "All near-drowning victims should be evaluated at a hospital — delayed pulmonary complications can occur."
                ),
                Arrays.asList(
                        "Do NOT enter the water unless trained as a lifeguard.",
                        "Do NOT attempt to drain water from the lungs — focus on CPR.",
                        "Do NOT leave the person alone even if they appear recovered."
                ),
                "always"
        ));

        return tips;
    }

    /**
     * Returns a list of all unique category names for the filter chips.
     */
    public static List<String> getCategories() {
        return Arrays.asList(
                "All",
                "Cardiac",
                "Breathing",
                "Bleeding",
                "Burns",
                "Fractures",
                "Shock",
                "Poisoning",
                "Head & Brain",
                "Neurological",
                "Diabetic",
                "Allergic",
                "Heat & Cold",
                "Eye",
                "Water"
        );
    }
}
