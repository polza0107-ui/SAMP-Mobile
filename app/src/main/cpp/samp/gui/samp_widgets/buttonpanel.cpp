#include "../../main.h"
#include "../gui.h"
#include "../../game/game.h"
#include "../../net/netgame.h"
#include "../../net/localplayer.h"

extern UI* pUI;
extern CNetGame* pNetGame;
extern CGame *pGame;

ButtonPanel::ButtonPanel()
	: Layout(Orientation::HORIZONTAL)
{
	// Only 3 quick buttons: Y, N, and :: (connected to Wheel menu)
	m_bY = new Button("Y", UISettings::fontSize() / 2);
	m_bN = new Button("N", UISettings::fontSize() / 2);
	m_bWheel = new Button("::", UISettings::fontSize() / 2);

	m_bY->setCallback([]() {
		LocalPlayerKeys.bKeys[ePadKeys::KEY_YES] = true;
	});

	m_bN->setCallback([]() {
		LocalPlayerKeys.bKeys[ePadKeys::KEY_NO] = true;
	});

	// "::" button opens the circular Wheel menu system (/wheel)
	m_bWheel->setCallback([]() {
		if (pNetGame) {
			pNetGame->SendChatCommand("/wheel");
		}
	});

	this->addChild(m_bY);
	this->addChild(m_bN);
	this->addChild(m_bWheel);
}