import { EnvironmentActions } from '../actions/environment.actions';

export interface IEnvironmentState {
    isMobile: boolean,
    isNavOpen: boolean,
    isSearchOpen: boolean,
    redirectUrl: any,
    rootSegment: string,
    navUrl: string,
    keyWord: string
};

const initialState: IEnvironmentState = {
    isMobile: false,
    isNavOpen: true,
    isSearchOpen: false,
    redirectUrl: '/tasks',
    rootSegment: '',
    navUrl: 'p?id=outline',
    keyWord: ''
};

export const environmentReducer = (state = initialState, {type, payload}): IEnvironmentState => {
    switch (type) {
        case EnvironmentActions.SEARCH:
            return Object.assign({}, state, {
                keyWord: payload.keyWord
            });
        case EnvironmentActions.RESET_SEARCH:
            return Object.assign({}, state, {
                keyWord: ''
            });
        case EnvironmentActions.TOGGLE_NAV:
            return Object.assign({}, state, {
                isNavOpen: !state.isNavOpen
            });
        case EnvironmentActions.TOGGLE_SEARCH:
            return Object.assign({}, state, {
                isSearchOpen: !state.isSearchOpen
            });
        case EnvironmentActions.HIDE_NAV:
            return Object.assign({}, state, {
                isNavOpen: true,
                isSearchOpen: false
            });
        case EnvironmentActions.SET_REDIRECT_URL:
            return Object.assign({}, state, {
                redirectUrl: payload.redirectUrl
            });
        case EnvironmentActions.SET_NAV_URL:
            return Object.assign({}, state, {
                rootSegment: payload.rootSegment,
                navUrl: payload.navUrl
            });
        default:
            return state;
    }
};
